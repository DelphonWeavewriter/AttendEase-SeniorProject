package edu.nyitLI.attendease.ui

// TEAM NOTE: Fragment to host Google Map (SupportMapFragment).
// Keep map-only logic here; data loading/search will be added later.
//We request location while-in-use only. This is friendlier for battery & privacy.
//The blue dot is provided by Google Maps; it auto-updates as we receive location updates.
//We re-center the camera once (first fix) so users aren’t fighting the map if they pan around.
// The Recenter FAB lets them snap back to themselves.
//If users choose Approximate location, accuracy will be coarser (~3km). The blue dot still appears.
//for testing purposes(if you’re physically far, use the emulator’s Location controls to set a point
// on campus).

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import edu.nyitLI.attendease.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import android.view.*
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton


class CampusMapFragment : Fragment(), OnMapReadyCallback {

    private var map: GoogleMap? = null

    // TEAM: Fused location client + callback for streaming location updates
    private lateinit var fused: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var requestingUpdates = false
    private var firstFix = false

    // TEAM: Runtime permission launcher for precise/approximate location
    private val locationPermsLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                    (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
            if (granted) {
                enableMyLocationLayerAndStartUpdates()
            } else {
                Log.w("CampusMap", "Location permission denied by user")
            }
        }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout that contains the <fragment> SupportMapFragment
        val root = inflater.inflate(R.layout.fragment_campus_map, container, false)

        // Locate the nested SupportMapFragment and request the async map
        // Child fragment manager because the map is nested inside this Fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // TEAM: Init fused location here; safe because we have a context now
        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        // Recenter FAB
        root.findViewById<FloatingActionButton>(R.id.fabRecenter)?.setOnClickListener {
            recenterToLastKnownLocation()
        }

        return root
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            // TEAM NOTE: keep gestures; disable Map Toolbar for a cleaner UI
            uiSettings.isMapToolbarEnabled = false
            // shows the default "crosshair" button
            uiSettings.isMyLocationButtonEnabled = true
            // Zoom limits tuned for campus-level navigation
            setMinZoomPreference(15f)  // street / campus
            setMaxZoomPreference(21f)  // building level
        }

        Log.d("CampusMap", "Map is ready")

        // Bounding box that covers NYIT LI (Old Westbury).
        // You can tweak these a bit if you want tighter/looser panning.
        val nyitLiBounds = LatLngBounds(
            LatLng(40.8060, -73.6230), // SW corner
            LatLng(40.8260, -73.5920)  // NE corner
        )
        map?.setLatLngBoundsForCameraTarget(nyitLiBounds)
        map?.moveCamera(CameraUpdateFactory.newLatLngBounds(nyitLiBounds, 64))

        // TEAM: Kick off permission check → enable blue dot & start updates when granted
        ensureLocationPermission()

        val center = LatLng(40.816213, -73.60724) // campus center
        map?.addMarker(MarkerOptions().position(center).title("NYIT Long Island"))

        // Smoke test: drop a temporary marker to confirm rendering
        // (TEAM: delete later once real data is wired)
        map?.addMarker(
            MarkerOptions()
                .position(LatLng(40.8150, -73.0950))
                .title("Test marker")
        )
    }

    private fun ensureLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            enableMyLocationLayerAndStartUpdates()
        } else {
            // Request both; Android may grant only COARSE if user selects "approximate"
            locationPermsLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayerAndStartUpdates() {
        // TEAM: Only call after permission is granted
        map?.isMyLocationEnabled = true
        map?.uiSettings?.isMyLocationButtonEnabled = true

        // Fast path: animate to the last known location if available
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && !firstFix) {
                firstFix = true
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f))
            }
        }

        startLocationUpdates()
    }
    private fun createLocationRequest(): LocationRequest {
        // TEAM: Balanced power is good for campus walking; adjust as needed
        return LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, // ~10s target
            10_000L
        )
            .setMinUpdateIntervalMillis(5_000L)  // fastest you'll accept
            .setMaxUpdateDelayMillis(20_000L)    // batch tolerance
            .build()
    }

    private fun buildLocationCallback() = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            // TEAM: The blue dot updates automatically; no need to draw your own marker.
            if (!firstFix) {
                firstFix = true
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (requestingUpdates) return
        val fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        if (locationCallback == null) locationCallback = buildLocationCallback()
        fused.requestLocationUpdates(createLocationRequest(), locationCallback as LocationCallback, Looper.getMainLooper())
        requestingUpdates = true
        Log.d("CampusMap", "Location updates started")
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fused.removeLocationUpdates(it) }
        requestingUpdates = false
        Log.d("CampusMap", "Location updates stopped")
    }

    @SuppressLint("MissingPermission")
    private fun recenterToLastKnownLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            ensureLocationPermission()
            return
        }
        fused.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 17f))
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // TEAM: If permission is already granted, ensure updates resume
        if (map != null) ensureLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        // TEAM: Stop updates to save battery when not visible
        stopLocationUpdates()
    }


}
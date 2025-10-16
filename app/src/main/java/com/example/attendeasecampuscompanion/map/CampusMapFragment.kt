package com.example.attendeasecampuscompanion.map

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
import com.example.attendeasecampuscompanion.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.material.floatingactionbutton.FloatingActionButton

// --- NYIT (Old Westbury) key building coordinates ---

private val LATLNG_SEROTA   = com.google.android.gms.maps.model.LatLng(40.809875, -73.605744) // Serota Academic Center
private val LATLNG_RILAND   = com.google.android.gms.maps.model.LatLng(40.808917, -73.605100) // Riland (NYITCOM)
private val LATLNG_MIDGEC   = com.google.android.gms.maps.model.LatLng(40.802260, -73.598170) // Midge Karr Art & Design Ctr
private val LATLNG_EDHALL   = com.google.android.gms.maps.model.LatLng(40.799790, -73.596370) // Education Hall
private val LATLNG_ANNA_RUBIN = com.google.android.gms.maps.model.LatLng(40.81332, -73.60513)
private val LATLNG_SCHURE     = com.google.android.gms.maps.model.LatLng(40.81365, -73.60425)
private val LATLNG_THEOBALD   = com.google.android.gms.maps.model.LatLng(40.81296, -73.60435)
private val LATLNG_SALTEN     = com.google.android.gms.maps.model.LatLng(40.81381, -73.60554)

// TEAM NOTE: Classroom markers are anchored to building coords for now.
// Later I can place indoor/entrance-level pins if we collect precise points.
private val LATLNG_ROOM_ANNARUBIN_306 = LATLNG_ANNA_RUBIN
private val LATLNG_ROOM_ANNARUBIN_303 = LATLNG_ANNA_RUBIN
private val LATLNG_ROOM_SCHURE_227     = LATLNG_SCHURE

class CampusMapFragment : Fragment(), OnMapReadyCallback {

    private var map: GoogleMap? = null

    // TEAM: Fused location client + callback for streaming location updates
    private lateinit var fused: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var requestingUpdates = false
    private var firstFix = false

    // TEAM: Runtime permission launcher for precise/approximate location
    private val locationPermsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
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


        // CampusMapFragment.kt (inside onMapReady)
        val ai = requireContext().packageManager.getApplicationInfo(
            requireContext().packageName, android.content.pm.PackageManager.GET_META_DATA
        )
        val keyPrefix = ai.metaData.getString("com.google.android.geo.API_KEY")?.take(8)
        android.util.Log.d("MapsKeyCheck", "API key prefix: $keyPrefix")

        Log.d("CampusMap", "Map is ready")


        // ===================== CAMPUS BOUNDS (INSERTED) =====================
        // Include academic quad + NYITCOM + Art & Architecture cluster.
        // built bounds from all anchor buildings, then add a small cushion.

        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
        listOf(
            LATLNG_ANNA_RUBIN, LATLNG_SCHURE, LATLNG_THEOBALD, LATLNG_SALTEN,   // quad
            LATLNG_RILAND, LATLNG_SEROTA,                                       // medicine (NYITCOM)
            LATLNG_MIDGEC, LATLNG_EDHALL                                        // art & architecture
        ).forEach { boundsBuilder.include(it) }

        val campusCore = boundsBuilder.build()

    // ~220m padding each way (~0.002 deg). Tweak smaller/larger after testing.
        val campusBounds = com.google.android.gms.maps.model.LatLngBounds(
            com.google.android.gms.maps.model.LatLng(
                campusCore.southwest.latitude - 0.0016,
                campusCore.southwest.longitude - 0.0018
            ),
            com.google.android.gms.maps.model.LatLng(
                campusCore.northeast.latitude + 0.0014,
                campusCore.northeast.longitude + 0.0012
            )
        )

// Keep the camera target inside campus; user can’t pan outside this rectangle
        map?.setLatLngBoundsForCameraTarget(campusBounds)

// Prevent zooming so far out that dragging “escapes” the box (tune as needed)
        map?.setMinZoomPreference(16.3f)
        map?.setMaxZoomPreference(20.5f)

// Start centered roughly between Schure (quad) and Serota (NYITCOM)
        val startCenter = com.google.android.gms.maps.model.LatLng(
            (LATLNG_SCHURE.latitude + LATLNG_SEROTA.latitude) / 2.0,
            (LATLNG_SCHURE.longitude + LATLNG_SEROTA.longitude) / 2.0
        )
        map?.moveCamera(
            com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(startCenter, 16.8f)
        )



        // ===================== MARKERS  =====================
        val buildingIcon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
        )
        val classroomIcon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
        )

        // Buildings
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_ANNA_RUBIN)
                .icon(buildingIcon)
                .title("Anna Rubin Hall")
                .snippet("Academic building")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_SCHURE)
                .icon(buildingIcon)
                .title("Harry J. Schure Hall")
                .snippet("Academic & student services")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_THEOBALD)
                .icon(buildingIcon)
                .title("Theobald Science Center")
                .snippet("Science & labs")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_SALTEN)
                .icon(buildingIcon)
                .title("Salten Hall")
                .snippet("Library & lounges")
        )

        // Classrooms (anchored at their building for now)
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_ROOM_ANNARUBIN_306)
                .icon(classroomIcon)
                .title("Anna Rubin — Room 306")
                .snippet("Classroom")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_ROOM_ANNARUBIN_303)
                .icon(classroomIcon)
                .title("Anna Rubin — Room 303")
                .snippet("Classroom")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_ROOM_SCHURE_227)
                .icon(classroomIcon)
                .title("Harry J. Schure — Room 227")
                .snippet("Classroom")
        )

        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_RILAND)
                .icon(buildingIcon)
                .title("Riland (NYITCOM)")
                .snippet("Academic Health Care Center")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_SEROTA)
                .icon(buildingIcon)
                .title("Serota Academic Center (NYITCOM)")
                .snippet("Medicine & Health Sciences")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_MIDGEC)
                .icon(buildingIcon)
                .title("Midge Karr Art & Design Center")
                .snippet("School of Architecture & Design")
        )
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_EDHALL)
                .icon(buildingIcon)
                .title("Education Hall")
                .snippet("Architecture & Design")
        )


        // TEAM: Kick off permission check → enable blue dot & start updates when granted

        ensureLocationPermission()

        val center = LatLng(40.816213, -73.60724) // campus center
        map?.addMarker(MarkerOptions().position(center).title("NYIT Long Island"))


    }

    private fun ensureLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            enableMyLocationLayerAndStartUpdates()
            android.util.Log.d("CampusMap", "Permission granted → enabling location layer")
            enableMyLocationLayerAndStartUpdates()
        } else {
            android.util.Log.d("CampusMap", "Requesting location permission…")
            // Request both; Android may grant only COARSE if user selects "approximate"
            locationPermsLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayerAndStartUpdates() {

        android.util.Log.d("CampusMap", "Enabling location layer…")
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
package edu.nyitLI.attendease.ui

// TEAM NOTE: Fragment to host Google Map (SupportMapFragment).
// Keep map-only logic here; data loading/search will be added later.


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

class CampusMapFragment : Fragment(), OnMapReadyCallback {

    private var map: GoogleMap? = null



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout that contains the <fragment> SupportMapFragment
        val root = inflater.inflate(R.layout.fragment_campus_map, container, false)

        // Child fragment manager because the map is nested inside this Fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            // TEAM NOTE: keep gestures; disable Map Toolbar for a cleaner UI
            uiSettings.isMapToolbarEnabled = false
            // Zoom limits tuned for campus-level navigation
            setMinZoomPreference(15f)  // street / campus
            setMaxZoomPreference(21f)  // building level
        }

        Log.d("CampusMap", "Map is ready")

        // Bounding box that snugly covers NYIT LI (Old Westbury).
        // You can tweak these a bit if you want tighter/looser panning.
        val nyitLiBounds = LatLngBounds(
            LatLng(40.8060, -73.6230), // SW corner
            LatLng(40.8260, -73.5920)  // NE corner
        )
        map?.setLatLngBoundsForCameraTarget(nyitLiBounds)
        map?.moveCamera(CameraUpdateFactory.newLatLngBounds(nyitLiBounds, 64))

        val center = LatLng(40.816213, -73.60724) // campus center (Riland area)
        map?.addMarker(MarkerOptions().position(center).title("NYIT Long Island"))

        // Smoke test: drop a temporary marker to confirm rendering
        // (TEAM: delete later once real data is wired)
        map?.addMarker(
            MarkerOptions()
                .position(LatLng(40.8150, -73.0950))
                .title("Test marker")
        )
    }
}
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
import org.json.JSONObject
import org.json.JSONArray
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polyline
import android.location.Location
import android.widget.Toast
import com.google.android.gms.maps.model.PolylineOptions
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
// Rushil: Graph + routing model imports.
import com.example.attendeasecampuscompanion.map.CampusGraph
import com.example.attendeasecampuscompanion.map.NavNode
import com.example.attendeasecampuscompanion.map.TravelMode
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog







// --- NYIT (Old Westbury) key building coordinates ---

//  Parking lot centroids (from Mapcarta / OSM, Fine Arts approx. 650 ft south of Midge Karr)
private val LATLNG_PARKING_NORTH = LatLng(40.813700, -73.609410)
private val LATLNG_PARKING_SOUTH = LatLng(40.809350, -73.604260)
// TEAM (Rushil): Fine Arts Parking is approximated from Mapcarta as ~200m south of Midge Karr.
// Adjust these if you find a more precise center visually in Android Studio.
private val LATLNG_PARKING_FINE_ARTS = LatLng(40.800460, -73.598170)

private val LATLNG_SEROTA   = com.google.android.gms.maps.model.LatLng(40.81033961078047, -73.6055924044658) // Serota Academic Center
private val LATLNG_RILAND   = com.google.android.gms.maps.model.LatLng(40.80948290749356, -73.60568361897101) // Riland (NYITCOM)
private val LATLNG_MIDGEC   = com.google.android.gms.maps.model.LatLng(40.802260, -73.598170) // Midge Karr Art & Design Ctr
private val LATLNG_EDHALL   = com.google.android.gms.maps.model.LatLng(40.799790, -73.596370) // Education Hall
private val LATLNG_ANNA_RUBIN = com.google.android.gms.maps.model.LatLng(40.81332, -73.60513)
private val LATLNG_SCHURE     = com.google.android.gms.maps.model.LatLng(40.81365, -73.60425)
private val LATLNG_THEOBALD   = com.google.android.gms.maps.model.LatLng(40.81296, -73.60435)
private val LATLNG_SALTEN     = com.google.android.gms.maps.model.LatLng(40.81381, -73.60554)

// TEAM NOTE: Classroom markers are anchored to building coords for now.
// Later I can place indoor/entrance-level pins if we collect precise points.
//Room 306: 40.81341478105639, -73.60517429269679
//Room 303: 40.81321380974355, -73.60541569087574
//Room 227: 40.813808602201426, -73.60447959964215
private val LATLNG_ROOM_ANNARUBIN_306 = com.google.android.gms.maps.model.LatLng(40.81341478105639, -73.60517429269679)
private val LATLNG_ROOM_ANNARUBIN_303 = com.google.android.gms.maps.model.LatLng(40.81321380974355, -73.60541569087574)
private val LATLNG_ROOM_SCHURE_227     =  com.google.android.gms.maps.model.LatLng(40.813808602201426, -73.60447959964215)

// add near: private var firstFix = false, etc.
private lateinit var originalBounds: LatLngBounds
private var mapTopPaddingPx: Int = 0

// Rushil: Slightly larger bounds for "am I close enough to campus to use nav?".
private lateinit var navBounds: LatLngBounds



class CampusMapFragment : Fragment(), OnMapReadyCallback {
    companion object {
        private const val RENDER_MARGIN_METERS = 150.0        // you can tune to 200 if needed
        private const val OUT_OF_BOUNDS_TOLERANCE_DEG = 1e-5  // tiny tolerance to avoid edge jitter
    }

    private var map: GoogleMap? = null

    // TEAM: Fused location client + callback for streaming location updates
    private lateinit var fused: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var requestingUpdates = false
    private var firstFix = false
    private var initialFramingDone = false

    private lateinit var buildingIcon: BitmapDescriptor
    private lateinit var classroomIcon: BitmapDescriptor

    // Rushil: Mode toggle buttons so user can switch between walking and driving.
    private lateinit var navModeToggleGroup: MaterialButtonToggleGroup
    private lateinit var walkModeButton: MaterialButton
    private lateinit var carModeButton: MaterialButton

    // Rushil: Parking UI row so the user can see/change which lot is used in car navigation.
    private lateinit var navParkingContainer: View
    private lateinit var navParkingText: TextView
    private lateinit var navChangeParkingButton: Button



    // TEAM (Rushil): separate icon so parking stands out from buildings on the map.
    private var parkingIcon: BitmapDescriptor? = null


    // Rushil: Represents one of the main campus parking lots. I keep it very simple:
    // an id, display name, and LatLng for the lot center/entrance.
    private data class ParkingLot(
        val id: String,
        val name: String,
        val latLng: LatLng
    )

    // Rushil: Hard-coded list of the three main parking lots on the LI campus.
// The LatLng constants were defined earlier (LATLNG_PARKING_NORTH etc.).
    private val parkingLots: List<ParkingLot> = listOf(
        ParkingLot(
            id = "parking_north",
            name = "North Parking",
            latLng = LATLNG_PARKING_NORTH
        ),
        ParkingLot(
            id = "parking_south",
            name = "South Parking",
            latLng = LATLNG_PARKING_SOUTH
        ),
        ParkingLot(
            id = "parking_fine_arts",
            name = "Fine Arts Parking",
            latLng = LATLNG_PARKING_FINE_ARTS
        )
    )


    // Rushil: This will hold my campus path graph once I load it from JSON (Phase 2).
    private var campusGraph: CampusGraph? = null

    // Rushil: While this is true, the camera will "follow" my location during navigation.
    // Once I manually drag the map, I'll set this to false so the camera stops snapping back.
    private var followUserDuringNav: Boolean = false

    // Rushil: I'll keep a reference to the recenter FAB so I can move it when the nav panel is visible.
    private lateinit var fabRecenter: FloatingActionButton

    // Rushil: Cache of the nav panel's height in pixels so I can position the FAB just above it.
    private var navPanelHeightPx: Int = 0



    // TEAM NOTE (Rushil): Simple enum to track what "mode" the map is in for navigation.
    private enum class NavigationState {
        Idle,      // No destination selected
        Preview,   // Marker selected, showing "Navigate" option
        Active     // Actively navigating with a route drawn
    }

    // Rushil: If null, I'll auto-pick the best lot. If non-null,
    // I route specifically via this lot for car→parking→walk.
    private var selectedParkingLot: ParkingLot? = null

    // Rushil: Speeds in meters per second for ETA calculations.
    // ~1.4 m/s ≈ 5 km/h (normal walking pace).
    private val walkingSpeedMetersPerSec = 1.4

    // Rushil: Campus-safe driving speed. You can tweak between 4–8 m/s depending
    // on how optimistic you want ETAs to be. 5 m/s ≈ 18 km/h (~11 mph).
    private val drivingSpeedMetersPerSec = 5.0


    // Rushil: Overall navigation mode for ETA/speeds and route decisions.
    // WALK = normal walking navigation along campus paths.
    // CAR  = driving navigation (same route for now, but faster ETA).
    private enum class NavMode {
        WALK,
        CAR
    }

    // Rushil: Default to walking mode; I can switch this to CAR from the UI later.
    private var currentNavMode: NavMode = NavMode.WALK


    // TEAM NOTE (Rushil): Keep reference to the currently selected marker (if any).
    private var selectedMarker: Marker? = null

    // TEAM NOTE (Rushil): Track the current navigation state.
    private var navigationState: NavigationState = NavigationState.Idle

    // TEAM NOTE (Rushil): Polyline for the simple straight-line route in Phase 1.
    private var navigationPolyline: Polyline? = null

    // TEAM NOTE (Rushil): Last known user location (updated from location callbacks).
    private var lastKnownLocation: Location? = null

    // Rushil: Store the full polyline of the current route (user → destination).
// This includes the start point, all graph nodes, and the final destination point.
    private var currentRoutePoints: List<LatLng> = emptyList()

    // Rushil: Cumulative distances along currentRoutePoints in meters.
// Example: [0, 10, 25, 40] means:
//  - point 0 at 0m
//  - point 1 at 10m from start
//  - point 2 at 25m from start
//  - point 3 at 40m from start (total route length = 40m)
    private var currentRouteCumulativeMeters: List<Double> = emptyList()

    // Rushil: Convenience getter for the total route length in meters.
    private val currentRouteTotalMeters: Double
        get() = if (currentRouteCumulativeMeters.isNotEmpty()) {
            currentRouteCumulativeMeters.last()
        } else {
            0.0
        }


    // TEAM NOTE (Rushil): References to the nav panel views in the layout.
    private lateinit var navPanelContainer: View
    private lateinit var navDestinationText: TextView
    private lateinit var navDistanceText: TextView
    private lateinit var navEtaText: TextView
    private lateinit var navArrivalTimeText: TextView
    private lateinit var navActionButton: Button


    // --- Search UI ---
    private var searchInput: TextInputEditText? = null
    private var suggestionsList: RecyclerView? = null
    private lateinit var suggestionsAdapter: SuggestionsAdapter

    // --- Registry / markers ---
    private data class Place(val title: String, val snippet: String?, val latLng: LatLng)
    private val buildingPlaces = mutableListOf<Place>()
    private val buildingMarkers = mutableMapOf<String, com.google.android.gms.maps.model.Marker>()

    // visible selection
    private var currentVisibleMarkerTitle: String? = null

    // debounce for search
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    private inner class SuggestionsAdapter(
        private val onClick: (Place) -> Unit
    ) : RecyclerView.Adapter<SuggestionsAdapter.VH>() {

        private val items = mutableListOf<Place>()

        inner class VH(val view: android.widget.TextView) : RecyclerView.ViewHolder(view) {
            fun bind(p: Place) {
                view.text = p.title
                view.setOnClickListener { onClick(p) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = android.widget.TextView(parent.context).apply {
                setPadding(24, 24, 24, 24)
                textSize = 16f
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        fun submit(list: List<Place>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }
    }


    private fun addStyledMarker(title: String, pos: LatLng, snippet: String?) {
        map?.addMarker(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(pos)
                .title(title)
                .snippet(snippet)
        )
    }


    // Marker snippets to align with your original style
    private val markerSnippets = mapOf(

        "Gerry House" to "Campus building",
        "Tower House" to "Campus building",
        "President's Stadium" to "Athletics",
        "Angelo Lorenzo Memorial Baseball Field" to "Athletics",
        "NYIT Softball Complex" to "Athletics",
        "Biomedical Research, Innovation, and Imaging Center (500 Building)" to "Medicine & Health Sciences",
        "Rockefeller Hall" to "Medicine & Health Sciences",
        "Maintenance Barn" to "Activities/Recreation",
        "Food Service" to "Activities/Recreation",
        "Student Activity Center" to "Activities/Recreation",
        "Recreation Hall" to "Activities/Recreation",
        "Balding House" to "Activities/Recreation",
        "Green Lodge" to "Activities/Recreation",
        "Whitney Lane House" to "Campus building",



        )


    // Expand bounds by meters asymmetrically (fetch-only; does NOT affect camera)
    private fun expandBoundsMeters(
        src: LatLngBounds,
        north: Double = 80.0,
        south: Double = 220.0,
        east: Double  = 80.0,
        west: Double  = 80.0
    ): LatLngBounds {
        val sw = src.southwest
        val ne = src.northeast
        val southPt = offsetMeters(sw, south, 180.0) // due south
        val westPt  = offsetMeters(sw, west,  270.0) // due west
        val northPt = offsetMeters(ne, north,   0.0) // due north
        val eastPt  = offsetMeters(ne, east,   90.0) // due east
        return LatLngBounds(
            LatLng(southPt.latitude, westPt.longitude),
            LatLng(northPt.latitude, eastPt.longitude)
        )
    }

    // --- Name normalization + fuzzy scoring ---
    private fun normName(s: String): String =
        s.lowercase()
            .replace("[’'`]".toRegex(), "")    // drop apostrophes
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun jaccard(a: String, b: String): Double {
        val ta = normName(a).split(" ").filter { it.isNotBlank() }.toSet()
        val tb = normName(b).split(" ").filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        return inter / union
    }

    private fun postToUi(block: () -> Unit) {
        if (!isAdded) return
        activity?.runOnUiThread {
            if (!isAdded) return@runOnUiThread
            block()
        }
    }

    // ----- Target config: lets us control display name + multiple search patterns -----
    private data class Target(
        val displayName: String,
        val isField: Boolean,             // true for stadium/fields (marker-only), false for buildings (polygon)
        val addMarkerIfPolygonMissing: Boolean = true, // for polygons: should we also add a marker?
        val suppressMarker: Boolean = false,           // set true if you already add a marker elsewhere
        val namePatterns: List<String>                 // Overpass "name~" patterns to match OSM names/aliases
    )

    // Build a single Overpass query for all targets within bounds.
    private fun buildOverpassQuery(targets: List<Target>, bbox: LatLngBounds): String {
        val s = bbox.southwest.latitude
        val w = bbox.southwest.longitude
        val n = bbox.northeast.latitude
        val e = bbox.northeast.longitude

        val buildingPatterns = targets.filter { !it.isField }.flatMap { it.namePatterns }
        val fieldPatterns    = targets.filter {  it.isField }.flatMap { it.namePatterns }

        val buildingsRegex = if (buildingPatterns.isEmpty()) null else buildingPatterns.joinToString("|")
        val fieldsRegex    = if (fieldPatterns.isEmpty())    null else fieldPatterns.joinToString("|")

        val sb = StringBuilder()
        sb.append("[out:json][timeout:25];(")
        if (buildingsRegex != null) {
            sb.append("""way["building"]["name"~"(${buildingsRegex})",i]($s,$w,$n,$e);""")
            sb.append("""relation["building"]["name"~"(${buildingsRegex})",i]($s,$w,$n,$e);""")
        }
        if (fieldsRegex != null) {
            sb.append("""way["leisure"~"pitch|stadium"]["name"~"(${fieldsRegex})",i]($s,$w,$n,$e);""")
            sb.append("""relation["leisure"~"pitch|stadium"]["name"~"(${fieldsRegex})",i]($s,$w,$n,$e);""")
        }
        sb.append(");out body geom;>;out skel qt;")
        return sb.toString()
    }

    // ---------- OSM/Overpass + drawing helpers ----------

    private fun polygonCentroid(points: List<LatLng>): LatLng {
        var sx = 0.0; var sy = 0.0
        points.forEach { p -> sx += p.latitude; sy += p.longitude }
        val n = points.size.coerceAtLeast(1)
        return LatLng(sx / n, sy / n)
    }

    private fun addBuildingPolygonGoogleStyle(
        name: String,
        points: List<LatLng>,
        addMarker: Boolean
    ) {
        if (points.isEmpty()) return
        val opts = com.google.android.gms.maps.model.PolygonOptions()
            .add(*points.toTypedArray())
            .also { if (points.first() != points.last()) it.add(points.first()) }
            .fillColor(android.graphics.Color.argb(150, 214, 214, 214)) // light grey, semi
            .strokeColor(android.graphics.Color.rgb(170, 170, 170))     // thin grey
            .strokeWidth(1.2f)
        map?.addPolygon(opts)

        if (addMarker) {
            addBuildingMarkerKeepRef(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(polygonCentroid(points))
                    .title(name)
                    .icon(buildingIcon)
            )
        }

    }

    // Convert Overpass element.geometry[] to LatLng ring(s)
    private fun geometryArrayToRings(geomArr: org.json.JSONArray): List<List<LatLng>> {
        // For a simple building "way", geometry[] will be the outer ring in order.
        val ring = mutableListOf<LatLng>()
        for (i in 0 until geomArr.length()) {
            val p = geomArr.getJSONObject(i)
            ring += LatLng(p.getDouble("lat"), p.getDouble("lon"))
        }
        return listOf(ring)
    }

    // Some relations (multipolygons) have "members" ways with geometries; we flatten outer rings.
    private fun extractRelationRings(elem: org.json.JSONObject): List<List<LatLng>> {
        val members = elem.optJSONArray("members") ?: return emptyList()
        val rings = mutableListOf<List<LatLng>>()
        for (i in 0 until members.length()) {
            val m = members.getJSONObject(i)
            if (m.optString("role") == "outer" && m.has("geometry")) {
                rings += geometryArrayToRings(m.getJSONArray("geometry"))[0]
            }
        }
        return rings
    }

// --- Building polygon helpers (drop inside CampusMapFragment class) ---

    private fun fetchAndRenderCampusFootprints(bbox: LatLngBounds) {
        val SUPPRESS = true
        val SHOW     = false

        data class Target(
            val displayName: String,
            val isField: Boolean,                // fields/stadium => marker-only
            val suppressMarker: Boolean = false, // true if you already add a marker elsewhere
            val namePatterns: List<String>       // regex OR plain parts
        )

        // Your list with aliases
        val targets = listOf(
            // Security / Facilities
            Target("Simonson House", false, suppressMarker = SHOW, namePatterns = listOf("Simonson\\s*House")),
            Target("Digital Print Center", false, suppressMarker = SHOW, namePatterns = listOf("Digital\\s*Print\\s*Center","Print\\s*Center")),

            // Other
            Target("North House", false, suppressMarker = SHOW, namePatterns = listOf("North\\s*House")),
            Target("Sculpture Barn", false, suppressMarker = SHOW, namePatterns = listOf("Sculpture\\s*Barn")),

            // NYITCOM
            Target("Rockefeller Hall", false, suppressMarker = SHOW, namePatterns = listOf("Rockefeller\\s*Hall")),
            Target("Biomedical Research, Innovation, and Imaging Center (500 Building)", false, suppressMarker = SHOW,
                namePatterns = listOf("Biomedical\\s*Research.*Imaging\\s*Center","BRIIC","500\\s*Building")),

            // Activities / Recreation (buildings)
            Target("Maintenance Barn", false, suppressMarker = SHOW, namePatterns = listOf("Maintenance\\s*Barn")),
            Target("Student Activity Center", false, suppressMarker = SHOW, namePatterns = listOf("Student\\s*Activity\\s*Center","Student\\s*Activities\\s*Center")),
            Target("Recreation Hall", false, suppressMarker = SHOW, namePatterns = listOf("Recreation\\s*Hall")),
            Target("Food Service", false, suppressMarker = SHOW, namePatterns = listOf("Food\\s*Service","Dining","Cafeteria")),
            Target("Green Lodge", false, suppressMarker = SHOW, namePatterns = listOf("Green\\s*Lodge")),
            Target("Balding House", false, suppressMarker = SHOW, namePatterns = listOf("Balding\\s*House")),
            Target("Gerry House", false, suppressMarker = SHOW, namePatterns = listOf("Gerry\\s*House")),
            Target("Tower House", false, suppressMarker = SHOW, namePatterns = listOf("Tower\\s*House")),

            // Art & Architecture (polygons yes; markers suppressed)
            Target("Midge Karr Art & Design Center", false, suppressMarker = SUPPRESS, namePatterns = listOf("Midge\\s*Karr.*Design\\s*Center","Midge\\s*Karr")),
            Target("Education Hall", false, suppressMarker = SUPPRESS, namePatterns = listOf("Education\\s*Hall","Education\\s*Building","School\\s*of\\s*Architecture.*")),

            // Health Sciences (polygon yes; marker suppressed)
            Target("Riland (NYITCOM)", false, suppressMarker = SUPPRESS, namePatterns = listOf("Riland(\\s|\\b).*")),

            // Sports (marker-only)
            Target("President's Stadium", true, namePatterns = listOf("President'?s?\\s*Stadium")),
            Target("Angelo Lorenzo Memorial Baseball Field", true, namePatterns = listOf("Angelo\\s*Lorenzo.*Baseball\\s*Field","Baseball\\s*Field")),
            Target("NYIT Softball Complex", true, namePatterns = listOf("Softball\\s*Complex","Softball\\s*Field"))
        )

        // Normalize + fuzzy helpers
        fun normName(s: String): String =
            s.lowercase()
                .replace("[’'`]".toRegex(), "")
                .replace("[^a-z0-9 ]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()

        fun jaccard(a: String, b: String): Double {
            val ta = normName(a).split(" ").filter { it.isNotBlank() }.toSet()
            val tb = normName(b).split(" ").filter { it.isNotBlank() }.toSet()
            if (ta.isEmpty() || tb.isEmpty()) return 0.0
            val inter = ta.intersect(tb).size.toDouble()
            val union = ta.union(tb).size.toDouble()
            return inter / union
        }

        // Build broad Overpass query: fetch ALL named buildings, pitches, stadiums in bbox
        fun buildOverpassQuery(b: LatLngBounds): String {
            val s = b.southwest.latitude
            val w = b.southwest.longitude
            val n = b.northeast.latitude
            val e = b.northeast.longitude
            return """
            [out:json][timeout:25];
            (
              way["building"]["name"]($s,$w,$n,$e);
              relation["building"]["name"]($s,$w,$n,$e);
              way["leisure"~"pitch|stadium"]["name"]($s,$w,$n,$e);
              relation["leisure"~"pitch|stadium"]["name"]($s,$w,$n,$e);
            );
            out body geom;
            >;
            out skel qt;
        """.trimIndent()
        }

        // Geometry helpers
        fun ringsFromWay(el: org.json.JSONObject): List<List<LatLng>> {
            val geom = el.optJSONArray("geometry") ?: return emptyList()
            val ring = ArrayList<LatLng>(geom.length())
            for (i in 0 until geom.length()) {
                val p = geom.getJSONObject(i)
                ring += LatLng(p.getDouble("lat"), p.getDouble("lon"))
            }
            return if (ring.isEmpty()) emptyList() else listOf(ring)
        }
        fun ringsFromRelation(el: org.json.JSONObject): List<List<LatLng>> {
            val members = el.optJSONArray("members") ?: return emptyList()
            val rings = mutableListOf<List<LatLng>>()
            for (i in 0 until members.length()) {
                val m = members.getJSONObject(i)
                if (m.optString("role") == "outer" && m.has("geometry")) {
                    rings += ringsFromWay(m)
                }
            }
            return rings
        }

        // ————— fetch —————
        Thread {
            try {
                val url = java.net.URL("https://overpass-api.de/api/interpreter")
                val body = "data=" + java.net.URLEncoder.encode(buildOverpassQuery(bbox), "UTF-8")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 20000
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = org.json.JSONObject(resp)
                val elements = json.optJSONArray("elements") ?: org.json.JSONArray()

                // Collect features with ALL tag strings we can match against
                data class OsmFeature(val namePool: List<String>, val bestName: String, val rings: List<List<LatLng>>)

                val features = mutableListOf<OsmFeature>()
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val type = el.optString("type", "")

                    val tags = el.optJSONObject("tags") ?: org.json.JSONObject()
                    if (tags.length() == 0) continue

                    // Make a pool from all string tag values: name, alt_name, official_name, short_name, name:en, etc.
                    val pool = mutableListOf<String>()
                    var best = ""
                    val it = tags.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val v = tags.optString(k, "")
                        if (v.isNotBlank()) {
                            pool += v
                            if (k == "name") best = v
                        }
                    }
                    if (best.isBlank() && pool.isNotEmpty()) best = pool[0]

                    val rings = when {
                        type == "way" && el.has("geometry")      -> ringsFromWay(el)
                        type == "relation" && el.has("members") -> ringsFromRelation(el)
                        else -> emptyList()
                    }
                    if (rings.isNotEmpty()) features += OsmFeature(pool, best, rings)
                }

                requireActivity().runOnUiThread {
                    val found = mutableSetOf<String>()

                    targets.forEach { t ->
                        var match: OsmFeature? = null

                        // 1) regex vs any tag value
                        val regexes = t.namePatterns.mapNotNull {
                            try { Regex(it, RegexOption.IGNORE_CASE) } catch (_: Throwable) { null }
                        }
                        match = features.firstOrNull { f -> f.namePool.any { s -> regexes.any { rx -> rx.containsMatchIn(s) } } }

                        // 2) normalized contains (any tag value)
                        if (match == null) {
                            val tn = normName(t.displayName)
                            match = features.firstOrNull { f ->
                                f.namePool.any { s ->
                                    val ns = normName(s)
                                    ns.contains(tn) || tn.contains(ns)
                                }
                            }
                        }

                        // 3) fuzzy Jaccard (any tag value) ≥ 0.6
                        if (match == null) {
                            var bestScore = 0.0
                            var bestFeat: OsmFeature? = null
                            for (f in features) {
                                val top = f.namePool.maxOfOrNull { s -> jaccard(t.displayName, s) } ?: 0.0
                                if (top > bestScore) { bestScore = top; bestFeat = f }
                            }
                            if (bestScore >= 0.6) match = bestFeat
                        }

                        // Render
                        if (match != null) {
                            if (t.isField) {
                                val center = polygonCentroid(match.rings[0])
                                addBuildingMarkerKeepRef(
                                    com.google.android.gms.maps.model.MarkerOptions()
                                        .position(center)
                                        .title(t.displayName)
                                        .snippet(markerSnippets[t.displayName])
                                        .icon(buildingIcon)
                                )

                            } else {
                                val ring = match.rings[0]
                                val first = ring.first(); val last = ring.last()
                                val poly = com.google.android.gms.maps.model.PolygonOptions()
                                    .add(*ring.toTypedArray())
                                    .also { if (first.latitude != last.latitude || first.longitude != last.longitude) it.add(first) }
                                    .fillColor(android.graphics.Color.argb(150, 214, 214, 214))
                                    .strokeColor(android.graphics.Color.rgb(170, 170, 170))
                                    .strokeWidth(1.2f)
                                map?.addPolygon(poly)

                                if (!t.suppressMarker) {
                                    addBuildingMarkerKeepRef(
                                        com.google.android.gms.maps.model.MarkerOptions()
                                            .position(polygonCentroid(ring))
                                            .title(t.displayName)
                                            .snippet(markerSnippets[t.displayName])
                                            .icon(buildingIcon)
                                    )

                                }
                            }
                            found += t.displayName
                        } else {
                            Log.w("Overpass", "Still missing after fuzzy: ${t.displayName}")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("Overpass", "Fetch/render error: ${e.message}")
            }
        }.start()
    }





    private fun addBuildingPolygon(
        name: String,
        points: List<LatLng>,
        addMarker: Boolean = true
    ) {
        // Style similar to Google Maps buildings
        val poly = com.google.android.gms.maps.model.PolygonOptions()
            .add(*points.toTypedArray())
            // Close the ring if caller forgot (PolygonOptions closes automatically,
            // but adding the first point again ensures visual consistency)
            .also { if (points.first() != points.last()) it.add(points.first()) }
            .fillColor(android.graphics.Color.argb(140, 200, 200, 200)) // semi-grey
            .strokeColor(android.graphics.Color.rgb(160, 160, 160))      // thin grey
            .strokeWidth(1.2f)

        map?.addPolygon(poly)

        if (addMarker) {
            val center = polygonCentroid(points)
            addBuildingMarkerKeepRef(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(center)
                    .title(name)
                    .icon(buildingIcon)
            )
        }

    }

    // --- Geo helpers (replace SphericalUtil) ---
    private fun offsetMeters(origin: LatLng, meters: Double, bearingDeg: Double): LatLng {
        val R = 6378137.0 // WGS84 Earth radius (meters)
        val br = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)
        val dOverR = meters / R

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(dOverR) +
                    Math.cos(lat1) * Math.sin(dOverR) * Math.cos(br)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(br) * Math.sin(dOverR) * Math.cos(lat1),
            Math.cos(dOverR) - Math.sin(lat1) * Math.sin(lat2)
        )

        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    // We’ll call this instead of map?.addMarker directly for BUILDING markers.
// It records the exact Marker and hides it at startup.
    private fun addBuildingMarkerKeepRef(options: MarkerOptions): com.google.android.gms.maps.model.Marker? {
        val m = map?.addMarker(options) ?: return null
        val title = options.title ?: return m
        // keep reference + hide by default
        buildingMarkers[title] = m
        m.isVisible = false
        // register for search (only once)
        if (buildingPlaces.none { it.title == title }) {
            buildingPlaces += Place(title = title, snippet = m.snippet, latLng = m.position)
        }
        return m
    }

    // Classrooms or non-building markers can still use map?.addMarker directly.
// For search selection:
    private fun selectPlace(place: Place) {
        // Hide previous
        currentVisibleMarkerTitle?.let { prev ->
            buildingMarkers[prev]?.isVisible = false
        }

        val marker = buildingMarkers[place.title] ?: return
        marker.isVisible = true
        marker.showInfoWindow()
        currentVisibleMarkerTitle = place.title

        val gmap = map ?: return

        // --- Compute a layout-aware pixel offset (fraction of the map view size) ---
        // We'll push the camera right/down so NW-edge markers are comfortably in-frame.
        val rootView = view ?: return
        val mapWidth = rootView.width.coerceAtLeast(1)
        val mapHeight = rootView.height.coerceAtLeast(1)

        // Default gentle nudge
        var fracX = 0f   // 0 so normal selections are centered
        var fracY = 0f   // ^

        // Stronger nudge for Simonson (hard NW corner), moderate for DPC
        when (place.title) {
            "Simonson House" -> { fracX = 0.30f; fracY = 0.20f }   // tuneable
            "Digital Print Center" -> { fracX = 0.22f; fracY = 0.24f }
        }

        val pxX = (mapWidth * fracX).toInt()
        val pxY = (mapHeight * fracY).toInt()

        val proj = gmap.projection
        val screenPt = proj.toScreenLocation(marker.position)
        val shiftedPt = android.graphics.Point(screenPt.x + pxX, screenPt.y + pxY)
        val adjustedLatLng = proj.fromScreenLocation(shiftedPt)

        // --- Temporarily relax bounds a hair more for the animation, then restore ---
        // (A bit wider west/north helps the NW corner most.)
        val expanded = LatLngBounds(
            LatLng(originalBounds.southwest.latitude - 0.0004, originalBounds.southwest.longitude - 0.0020),
            LatLng(originalBounds.northeast.latitude + 0.0005, originalBounds.northeast.longitude + 0.0003)
        )
        // Rushil: Make a slightly bigger box for nav checks so GPS drift / edges still count as "on campus".
        navBounds = expandBoundsMeters(
            originalBounds,
            north = 300.0,   // tweak these numbers if still too tight/loose
            south = 150.0,
            east  = 150.0,
            west  = 300.0
        )

        // Also temporarily reduce top padding so it doesn't crowd the top edge
        val prevTopPad = mapTopPaddingPx
        val tempTopPad = (prevTopPad * 0.4f).toInt()  // e.g., 500 → 200 during the move

        gmap.setLatLngBoundsForCameraTarget(expanded)
        gmap.setPadding(0, tempTopPad, 0, 0)

        gmap.animateCamera(CameraUpdateFactory.newLatLngZoom(adjustedLatLng, 18f))

        gmap.setOnCameraIdleListener {
            // Restore your original clamp + padding after the animation settles
            map?.setLatLngBoundsForCameraTarget(originalBounds)
            map?.setPadding(0, prevTopPad, 0, 0)
            map?.setOnCameraIdleListener(null)
        }

        // Hide suggestions
        suggestionsList?.visibility = View.GONE
    }





    // Filter suggestions by prefix of the title (case-insensitive)
    private fun filterSuggestions(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            suggestionsAdapter.submit(emptyList())
            suggestionsList?.visibility = View.GONE
            // Also hide any visible marker when clearing search
            currentVisibleMarkerTitle?.let { buildingMarkers[it]?.isVisible = false }
            currentVisibleMarkerTitle = null
            return
        }
        // prefix first
        val results = buildingPlaces
            .filter { it.title.lowercase().startsWith(q) }
            .take(10)

        suggestionsAdapter.submit(results)
        suggestionsList?.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
    }


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


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreate(savedInstanceState)
        com.google.android.gms.maps.MapsInitializer.initialize(
            requireContext(),
            com.google.android.gms.maps.MapsInitializer.Renderer.LATEST
        ) { /* no-op */ }

        // Inflate the layout that contains the <fragment> SupportMapFragment
        val root = inflater.inflate(R.layout.fragment_campus_map, container, false)

        // Locate the nested SupportMapFragment and request the async map
        // Child fragment manager because the map is nested inside this Fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // TEAM: Init fused location here; safe because we have a context now
        fused = LocationServices.getFusedLocationProviderClient(requireContext())

        // Hook up search views
        searchInput = root.findViewById(R.id.searchInput)
        suggestionsList = root.findViewById<RecyclerView>(R.id.suggestionsList).apply {
            layoutManager = LinearLayoutManager(requireContext())
            suggestionsAdapter = SuggestionsAdapter { place ->
                // on suggestion click
                selectPlace(place)
            }
            adapter = suggestionsAdapter
        }
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // debounce
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                pendingSearch = Runnable { filterSuggestions(s?.toString().orEmpty()) }
                searchHandler.postDelayed(pendingSearch!!, 200)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Recenter FAB
        // Rushil: Keep a reference to the recenter FAB so I can move it relative to the nav panel.
        fabRecenter = root.findViewById(R.id.fabRecenter)
        fabRecenter.setOnClickListener {
            recenterToLastKnownLocation()
            suggestionsList?.visibility = View.GONE

            // Rushil: If I'm navigating and hit recenter, go back into follow mode.
            if (navigationState == NavigationState.Active) {
                followUserDuringNav = true
            }
        }


        // ================== NAV PANEL WIRED HERE ==================

        // Rushil: Hook up the nav panel views from the XML layout for Phase 1 navigation.
        navPanelContainer = root.findViewById(R.id.navPanelContainer)
        navDestinationText = root.findViewById(R.id.navDestinationText)
        navDistanceText = root.findViewById(R.id.navDistanceText)
        navEtaText = root.findViewById(R.id.navEtaText)
        navArrivalTimeText = root.findViewById(R.id.navArrivalTimeText)
        navActionButton = root.findViewById(R.id.navActionButton)

        // 🅿️ Parking controls
        navParkingContainer = root.findViewById(R.id.navParkingContainer)
        navParkingText = root.findViewById(R.id.navParkingText)
        navChangeParkingButton = root.findViewById(R.id.navChangeParkingButton)

        // Hide by default; only show in CAR mode with a non-parking destination.
        navParkingContainer.visibility = View.GONE

        navChangeParkingButton.setOnClickListener {
            // Only makes sense in CAR mode & when we have a destination.
            if (currentNavMode != NavMode.CAR || selectedMarker == null || isParkingMarker(selectedMarker)) {
                Toast.makeText(requireContext(), "Parking selection only applies to driving to buildings.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show a simple chooser dialog of the three parking lots.
            showParkingLotChooser()
        }


        // Rushil: Handle clicks on the main "Navigate / Stop" button.
        navActionButton.setOnClickListener {
            when (navigationState) {
                NavigationState.Preview -> {
                    // Rushil: Marker is selected and I tapped "Navigate" → start basic navigation.
                    startNavigationFromCurrentLocation()
                }
                NavigationState.Active -> {
                    // Rushil: I'm currently navigating and tapped "Stop" → end navigation.
                    stopNavigation()
                }
                NavigationState.Idle -> {
                    // Rushil: Shouldn't normally happen because panel is hidden in Idle, but just in case.
                    Toast.makeText(requireContext(), "Select a destination first.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Rushil: Hook up the Walk / Drive toggle.
        navModeToggleGroup = root.findViewById(R.id.navModeToggleGroup)
        walkModeButton = root.findViewById(R.id.btnModeWalk)
        carModeButton = root.findViewById(R.id.btnModeCar)

        navModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            when (checkedId) {
                R.id.btnModeWalk -> setNavMode(NavMode.WALK)
                R.id.btnModeCar  -> setNavMode(NavMode.CAR)
            }
        }

        // Rushil: Default to WALK mode visually + logically.
        navModeToggleGroup.check(R.id.btnModeWalk)
        setNavMode(NavMode.WALK)


        // Rushil: Watch the nav panel layout so I can learn its height once it's visible.
        navPanelContainer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height > 0) {
                navPanelHeightPx = v.height
                updateFabPositionRelativeToNavPanel()
            }
        }


        // ================== END NAV PANEL SECTION ==================
        // ================== PHASE 2: LOAD CAMPUS GRAPH (IF PRESENT) ==================
        try {
            // Rushil: Try to load my campus graph from res/raw/campus_graph.json.
            // Right now this will use a tiny placeholder; later I'll overwrite the JSON
            // with the real OSM-based data.
           campusGraph = CampusGraphLoader.loadFromRawJson(requireContext(), R.raw.campus_graph)

            android.util.Log.d("CampusMap", "Campus graph loaded: " +
                    "${campusGraph?.nodes?.size ?: 0} nodes, " +
                    "${campusGraph?.edges?.size ?: 0} edges")
        } catch (e: Exception) {
            // Rushil: If the file is missing or JSON is bad, don't crash the app.
            campusGraph = null
            android.util.Log.e("CampusMap", "Failed to load campus graph JSON", e)
        }
        // ================== END GRAPH LOAD ==================

        return root
    }





    private fun withTempBounds(
        tempBounds: LatLngBounds,
        action: () -> Unit,
        restoreBounds: LatLngBounds
    ) {
        val gmap = map ?: return
        gmap.setLatLngBoundsForCameraTarget(tempBounds)
        action()
        // restore after the camera settles
        gmap.setOnCameraIdleListener {
            map?.setLatLngBoundsForCameraTarget(restoreBounds)
            map?.setOnCameraIdleListener(null)
        }
    }



    override fun onMapReady(googleMap: GoogleMap) {

        map = googleMap.apply {
            // TEAM NOTE: keep gestures; disable Map Toolbar for a cleaner UI
            uiSettings.isMapToolbarEnabled = false
            // shows the default "crosshair" button
            uiSettings.isMyLocationButtonEnabled = false
            isBuildingsEnabled = true

            // Zoom limits tuned for campus-level navigation
            setMinZoomPreference(15f)  // street / campus
            setMaxZoomPreference(19.0f)  // building level
        }

        // 🔹 Initialize global icons here
        buildingIcon  = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        classroomIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        // TEAM (Rushil): orange pin for parking lots so they are easy to spot.
        parkingIcon   = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)

        // CampusMapFragment.kt (inside onMapReady)
        val ai = requireContext().packageManager.getApplicationInfo(
            requireContext().packageName, android.content.pm.PackageManager.GET_META_DATA
        )


        Log.d("CampusMap", "Map is ready")


// ===================== CAMPUS BOUNDS (NO SNAP-BACK) =====================
        val boundsBuilder = LatLngBounds.builder()
        listOf(
            LATLNG_ANNA_RUBIN, LATLNG_SCHURE, LATLNG_THEOBALD, LATLNG_SALTEN,
            LATLNG_RILAND, LATLNG_SEROTA,
            LATLNG_MIDGEC, LATLNG_EDHALL
        ).forEach { boundsBuilder.include(it) }

        val campusCore = boundsBuilder.build()

// 1) Your existing hard user box (unchanged numbers)
           originalBounds = LatLngBounds(
            LatLng(campusCore.southwest.latitude + 0.0012, campusCore.southwest.longitude - 0.0018),
            LatLng(campusCore.northeast.latitude + 0.0003, campusCore.northeast.longitude + 0.0001)
        )


// 3) Buildings ON (set on the real GoogleMap instance)
        googleMap.isBuildingsEnabled = true

// 4) Top padding (shifts viewport down a bit so south edge is more visible)
        mapTopPaddingPx = 500
        map?.setPadding(0, mapTopPaddingPx, 0, 0)


// 5) Target bounds (choose ONE):
// A) Keep users strictly inside originalBounds (hard clamp, no snap-back):
        map?.setLatLngBoundsForCameraTarget(originalBounds)
// B) Allow a little extra south panning to help rendering (looser clamp):
// map?.setLatLngBoundsForCameraTarget(engineBounds)

// 6) Zoom prefs
        map?.setMinZoomPreference(16.3f)
        map?.setMaxZoomPreference(20.5f)

// 7) Initial fit + tiny south nudge (do this once when the map is laid out)
        map?.setOnMapLoadedCallback {
            // Fit to your original hard box (respects the top padding you set above)
            map?.moveCamera(CameraUpdateFactory.newLatLngBounds(originalBounds, 0))

            suggestionsList?.visibility = View.GONE
            searchInput?.setText("")


            // Tiny nudge SOUTH so southern tiles are inside the frustum
            map?.cameraPosition?.target?.let { cur ->
                val shifted = offsetMeters(cur, 90.0, 180.0) // 90 m south; tweak 60–120
                map?.moveCamera(CameraUpdateFactory.newLatLng(shifted))
            }

            // If you added the initialFramingDone flag earlier, set it here:
            initialFramingDone = true




        }
        val fetchBounds = expandBoundsMeters(originalBounds, north = 80.0, south = 220.0, east = 80.0, west = 80.0)
        fetchAndRenderCampusFootprints(fetchBounds)


        // ===================== MARKERS  =====================
        // --- Manual fallbacks (only if you still don't see the markers) ---
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LatLng(40.814759114770865, -73.60981569632176))
                .title("Simonson House")
                .snippet(markerSnippets["SECURITY/FACILITIES"])
                .icon(buildingIcon)
        )


        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LatLng(40.81443837813061, -73.60894129780674))
                .title("Digital Print Center")
                .snippet(markerSnippets["SECURITY/FACILITIES"])
                .icon(buildingIcon)
        )

        //  Campus parking lots — explicit, so we can target them in multi-leg routing later.
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_PARKING_NORTH)
                .title("North Parking")
                .snippet("Campus parking lot (north)")
                .icon(parkingIcon)
        )

        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_PARKING_SOUTH)
                .title("South Parking")
                .snippet("Campus parking lot (south)")
                .icon(parkingIcon)
        )

        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_PARKING_FINE_ARTS)
                .title("Fine Arts Parking")
                .snippet("Campus parking lot (Fine Arts)")
                .icon(parkingIcon)
        )




        // Buildings
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_ANNA_RUBIN)
                .icon(buildingIcon)
                .title("Anna Rubin Hall")
                .snippet("Academic building")
        )
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_SCHURE)
                .icon(buildingIcon)
                .title("Harry J. Schure Hall")
                .snippet("Academic & student services")
        )
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_THEOBALD)
                .icon(buildingIcon)
                .title("Theobald Science Center")
                .snippet("Science & labs")
        )
        addBuildingMarkerKeepRef(
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

        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_RILAND)
                .icon(buildingIcon)
                .title("Riland Academic Center")
                .snippet("Academic Health Care Center")
        )
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_SEROTA)
                .icon(buildingIcon)
                .title("Serota Academic Center ")
                .snippet("Medicine & Health Sciences")
        )
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_MIDGEC)
                .icon(buildingIcon)
                .title("Midge Karr Art & Design Center")
                .snippet("School of Architecture & Design")
        )
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(LATLNG_EDHALL)
                .icon(buildingIcon)
                .title("Education Hall")
                .snippet("Architecture & Design")
        )
// Serota Academic Center  (OSM way 595698561)
        val SEROTA_POLY = listOf(
            LatLng(40.8103513, -73.6058427), // node 5676910493
            LatLng(40.8105884, -73.6055211), // node 5676910494
            LatLng(40.8103532, -73.6052184), // node 5676910495
            LatLng(40.8101398, -73.6055079), // node 5676910496
            LatLng(40.8101950, -73.6055789), // node 5676910497
            LatLng(40.8101713, -73.6056111)  // node 5676910498
        )
// Wisser Memorial Library  (OSM way 595698586)
        val WISSER_POLY = listOf(
            LatLng(40.8113514, -73.6041494), // node 5676910702
            LatLng(40.8111346, -73.6043773), // node 5676910703
            LatLng(40.8109648, -73.6040953), // node 5676910704
            LatLng(40.8111817, -73.6038674)  // node 5676910705
        )
// Draw polygons. Skip markers for buildings you already mark elsewhere:

        addBuildingPolygon(
            name = "Serota Academic Center",
            points = SEROTA_POLY,
            addMarker = false
        )

        addBuildingPolygon(
            name = "Wisser Memorial Library",
            points = WISSER_POLY,
            addMarker = false
        )

        // Add the building marker via keeper (so it's hidden until search)
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(polygonCentroid(WISSER_POLY))
                .title("Wisser Memorial Library")
                .snippet("Library") // keep/adjust to match what you want shown; do not change coords
                .icon(buildingIcon)
        )

        // Whitney Lane House — marker only (no polygon)
// Replace the coordinates with the exact lat/lng for Whitney Lane House.
        addBuildingMarkerKeepRef(
            com.google.android.gms.maps.model.MarkerOptions()
                .position(com.google.android.gms.maps.model.LatLng(
                    /* lat = */ 40.811638499483706,   /* TODO: put exact latitude */
                    /* lng = */ -73.60052834471715 /* TODO: put exact longitude */
                ))
                .title("Whitney Lane House")
                .snippet(markerSnippets["Whitney Lane House"]) // or a hardcoded string if you prefer
                .icon(buildingIcon)
        )
// TEAM NOTE (Rushil): When the user taps any building marker, enter navigation preview mode.
        googleMap.setOnMarkerClickListener { marker ->
            // If you have special markers (like "user location" or debug markers) that
            // should not trigger navigation, you can early-return here based on marker.tag.

            selectedMarker = marker
            enterNavigationPreview(marker)
            // Return true to consume the click and prevent default behavior (info window),
            // or false if you still want the info window to show. I'm using true for now.
            true
        }


        // Rushil: If I manually drag/zoom the map while navigating, I don't want the camera
        // to keep snapping back to my location. This listener turns off auto-follow on gestures.
        map?.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE &&
                navigationState == NavigationState.Active
            ) {
                followUserDuringNav = false
                // (Optional) I could show a toast once, but doing it every drag would be annoying.
                // Toast.makeText(requireContext(), "Camera free to pan. Use recenter to follow again.", Toast.LENGTH_SHORT).show()
            }
        }

        // TEAM: Kick off permission check → enable blue dot & start updates when granted

        ensureLocationPermission()




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
        map?.uiSettings?.isMyLocationButtonEnabled = false

        // Fast path: animate to the last known location if available
        fused.lastLocation.addOnSuccessListener { loc ->
            // Don’t hijack the camera until our initial framing is finished
            if (loc != null && initialFramingDone && !firstFix) {
                firstFix = true
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f)
                )
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
            // Only recenter after initial framing; otherwise the blue dot is enough.
            if (initialFramingDone && !firstFix) {
                firstFix = true
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f)
                )
            }
            // Rushil: Feed every new GPS fix into my nav logic so distance/ETA update live.
            onUserLocationUpdated(loc)
        }
    }


    // TEAM NOTE (Rushil): Central place to update our nav state whenever the user moves.
    private fun onUserLocationUpdated(location: Location) {
        lastKnownLocation = location

        if (navigationState == NavigationState.Active) {
            // Rushil: Always update stats while navigating.
            updateNavigationInfo()

            // Rushil: Only auto-move the camera if I'm in "follow" mode.
            if (followUserDuringNav) {
                val googleMap = map ?: return
                val userLatLng = LatLng(location.latitude, location.longitude)
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng))
            }
        } else if (navigationState == NavigationState.Preview) {
            updateNavigationInfo()
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

    // TEAM NOTE (Rushil): When a marker is selected, show the nav panel with a "Navigate" option.
    private fun enterNavigationPreview(marker: Marker) {
        navigationState = NavigationState.Preview

        // Show destination name in the panel.
        navDestinationText.text = "Destination: ${marker.title ?: "Selected location"}"

        // Update distance/ETA info based on current location if we have it.
        updateNavigationInfo()

        // Update button label for this state.
        navActionButton.text = "Navigate"

        // Make the panel visible.
        navPanelContainer.visibility = View.VISIBLE

        // Rushil: Nav panel is now visible, so move the FAB up above it.
        updateFabPositionRelativeToNavPanel()
    }



    // Rushil: Update the distance/ETA/arrival info shown in the nav panel.
    // Phase 3.5: If I have a route, use remaining distance along the route.
    // Otherwise fall back to simple straight-line distance.
    private fun updateNavigationInfo() {
        val marker = selectedMarker

        if (marker == null) {
            navDistanceText.text = "Distance: —"
            navEtaText.text = "ETA: —"
            navArrivalTimeText.text = "Arrival: —"
            return
        }

        val location = lastKnownLocation
        if (location == null) {
            // Rushil: We don't know where the user is yet. If we have a route, we could
            // show the total route distance, but for now I'll keep this simple and wait
            // for a GPS fix.
            navDistanceText.text = "Distance: (waiting for GPS...)"
            navEtaText.text = "ETA: —"
            navArrivalTimeText.text = "Arrival: —"
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)
        val destLatLng = marker.position

        val distanceMeters: Double

        if (currentRoutePoints.size >= 2 && currentRouteCumulativeMeters.size == currentRoutePoints.size) {
            // Rushil: We have a route and precomputed distances. Compute remaining distance.

            val results = FloatArray(1)

            // 1) Find the nearest route vertex to the user's current location.
            var bestIndex = 0
            var bestDistToVertex = Double.MAX_VALUE

            for (i in currentRoutePoints.indices) {
                val pt = currentRoutePoints[i]
                Location.distanceBetween(
                    userLatLng.latitude,
                    userLatLng.longitude,
                    pt.latitude,
                    pt.longitude,
                    results
                )
                val d = results[0].toDouble()
                if (d < bestDistToVertex) {
                    bestDistToVertex = d
                    bestIndex = i
                }
            }

            // 2) Remaining distance from that vertex to the end of the route.
            val remainingFromVertex = currentRouteTotalMeters - currentRouteCumulativeMeters[bestIndex]

            // 3) Approximate total remaining distance as:
            //    distance from user → nearest vertex + remaining along route.
            distanceMeters = bestDistToVertex + remainingFromVertex
        } else {
            // Rushil: No route info (probably just in preview or nav hasn't started yet).
            // Fall back to straight-line distance between user and destination.
            val results = FloatArray(1)
            Location.distanceBetween(
                userLatLng.latitude,
                userLatLng.longitude,
                destLatLng.latitude,
                destLatLng.longitude,
                results
            )
            distanceMeters = results[0].toDouble()
        }

        // Rushil: Choose speed based on current navigation mode.
        // For now, both WALK and CAR follow the same route geometry, but CAR uses
        // a faster speed so ETAs feel like driving instead of walking.
        val speedMetersPerSec = when (currentNavMode) {
            NavMode.WALK -> walkingSpeedMetersPerSec
            NavMode.CAR  -> drivingSpeedMetersPerSec
        }

        val etaSeconds = distanceMeters / speedMetersPerSec
        val etaMinutes = (etaSeconds / 60.0)


        // Rushil: Convert meters → miles for display only.
        // 1 meter = 0.000621371 miles, 1 meter = 3.28084 feet.
        val miles = distanceMeters * 0.000621371

        val distanceText = if (miles < 0.1) {
            // If less than ~0.1 miles, show in feet (feels more natural up close)
            val feet = distanceMeters * 3.28084
            "Distance: ${feet.toInt()} ft"
        } else {
            // Otherwise display miles with 2 decimals
            "Distance: %.2f mi".format(miles)
        }

        navDistanceText.text = distanceText


        // Format ETA.
        val etaText = if (etaMinutes < 1.0) {
            "ETA: < 1 min"
        } else {
            "ETA: ${etaMinutes.toInt()} min"
        }
        navEtaText.text = etaText

        // Compute estimated arrival time based on device clock.
        val etaMillis = (etaSeconds * 1000).toLong()
        val arrivalTimeMillis = System.currentTimeMillis() + etaMillis
        val arrivalDate = Date(arrivalTimeMillis)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        navArrivalTimeText.text = "Arrival: ${timeFormat.format(arrivalDate)}"
    }





    // Rushil: Called when I tap "Navigate" in preview mode.
    // Phase 3: Try to use the campus path graph to route along walkways; if that fails, fall back to straight line.
    private fun startNavigationFromCurrentLocation() {
        val marker = selectedMarker
        val location = lastKnownLocation

        if (marker == null) {
            Toast.makeText(requireContext(), "Select a destination first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (location == null) {
            Toast.makeText(requireContext(), "Waiting for your current location...", Toast.LENGTH_SHORT).show()
            return
        }

        val googleMap = map ?: run {
            Toast.makeText(requireContext(), "Map is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)
        val destLatLng = marker.position

        // Rushil: Enforce "must be on/near campus" rule before starting nav.
        if (::navBounds.isInitialized && !navBounds.contains(userLatLng)) {
            Toast.makeText(
                requireContext(),
                "Navigation is for on/near campus only.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val graph = campusGraph
        if (graph == null) {
            // Rushil: If the graph failed to load, fall back to simple straight-line nav.
            startStraightLineNavigation(userLatLng, destLatLng)
            return
        }

        // Decide if we should try multi-leg routing:
        //  - only in CAR mode
        //  - only if the destination is NOT itself a parking lot
        val shouldUseMultiLegCar = (currentNavMode == NavMode.CAR) && !isParkingMarker(marker)

        val finalPolylinePoints: List<LatLng>
        var viaParkingLotName: String? = null

        if (shouldUseMultiLegCar) {
            // Rushil: Try to build a car→parking→walk route.
            // If selectedParkingLot is set, force that lot. Otherwise auto-choose best.
            val multiLeg = buildCarThenWalkRoute(
                userLatLng = userLatLng,
                destLatLng = destLatLng,
                forcedParkingLot = selectedParkingLot
            )

            if (multiLeg != null) {
                finalPolylinePoints = multiLeg.polylinePoints

                // Remember which lot was actually used, even if it was auto-chosen.
                selectedParkingLot = multiLeg.parkingLot
                viaParkingLotName = multiLeg.parkingLot.name
            } else {
                // fallback walking route...

                // Rushil: If multi-leg fails (no car route to any lot, etc.),
                // fall back to a simple walking route along paths.
                val startNode = findNearestNode(userLatLng)
                val endNode = findNearestNode(destLatLng)

                if (startNode == null || endNode == null) {
                    Toast.makeText(
                        requireContext(),
                        "Could not snap to campus paths. Using straight-line route.",
                        Toast.LENGTH_SHORT
                    ).show()
                    startStraightLineNavigation(userLatLng, destLatLng)
                    return
                }

                val pathNodeIds = graph.shortestPathNodeIds(
                    startId = startNode.id,
                    endId = endNode.id,
                    allowedModes = setOf(TravelMode.WALK)
                )

                if (pathNodeIds.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No path found on campus network. Using straight-line route.",
                        Toast.LENGTH_SHORT
                    ).show()
                    startStraightLineNavigation(userLatLng, destLatLng)
                    return
                }

                val nodeIndex: Map<Int, NavNode> = graph.nodes.associateBy { it.id }
                val polylinePoints = mutableListOf<LatLng>()
                polylinePoints.add(userLatLng)
                for (nodeId in pathNodeIds) {
                    val node = nodeIndex[nodeId] ?: continue
                    polylinePoints.add(LatLng(node.lat, node.lng))
                }
                polylinePoints.add(destLatLng)

                finalPolylinePoints = polylinePoints
            }
        } else {
            // Rushil: WALK mode, or destination is already a parking lot.
            // Use a single-leg walking route along the graph.
            val startNode = findNearestNode(userLatLng)
            val endNode = findNearestNode(destLatLng)

            if (startNode == null || endNode == null) {
                Toast.makeText(
                    requireContext(),
                    "Could not snap to campus paths. Using straight-line route.",
                    Toast.LENGTH_SHORT
                ).show()
                startStraightLineNavigation(userLatLng, destLatLng)
                return
            }

            val pathNodeIds = graph.shortestPathNodeIds(
                startId = startNode.id,
                endId = endNode.id,
                allowedModes = setOf(TravelMode.WALK)
            )

            if (pathNodeIds.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No path found on campus network. Using straight-line route.",
                    Toast.LENGTH_SHORT
                ).show()
                startStraightLineNavigation(userLatLng, destLatLng)
                return
            }

            val nodeIndex: Map<Int, NavNode> = graph.nodes.associateBy { it.id }
            val polylinePoints = mutableListOf<LatLng>()
            polylinePoints.add(userLatLng)
            for (nodeId in pathNodeIds) {
                val node = nodeIndex[nodeId] ?: continue
                polylinePoints.add(LatLng(node.lat, node.lng))
            }
            polylinePoints.add(destLatLng)

            finalPolylinePoints = polylinePoints
        }

        // Rushil: Save this route so remaining-distance/ETA uses the actual path.
        setCurrentRoute(finalPolylinePoints)

        // Clear any existing route polyline.
        navigationPolyline?.remove()

        navigationPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(finalPolylinePoints)
                .width(12f)
        )

        // Fit camera around user + destination (route will also be inside).
        val builder = LatLngBounds.builder()
            .include(userLatLng)
            .include(destLatLng)
        val bounds = builder.build()
        val padding = 150
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))

        navigationState = NavigationState.Active
        navActionButton.text = "Stop"
        updateNavigationInfo()

        followUserDuringNav = true

        // 🅿️ Show parking row only when using car multi-leg to a non-parking destination.
        if (shouldUseMultiLegCar && selectedParkingLot != null) {
            navParkingContainer.visibility = View.VISIBLE
            navParkingText.text = "Parking: ${selectedParkingLot?.name}"
        } else {
            navParkingContainer.visibility = View.GONE
        }

        if (viaParkingLotName != null) {
            Toast.makeText(
                requireContext(),
                "Navigation started: drive to $viaParkingLotName, then walk to ${marker.title}.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                "Navigation started along campus paths.",
                Toast.LENGTH_SHORT
            ).show()
        }

    }







    // Rushil: This is my original Phase 1 behavior: draw a straight line from user to destination.
    // I'm keeping it as a helper so I can fall back to it if the graph fails for any reason.
    private fun startStraightLineNavigation(userLatLng: LatLng, destLatLng: LatLng) {
        val googleMap = map ?: run {
            Toast.makeText(requireContext(), "Map is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        // Rushil: Define the full route as just a simple 2-point line.
        val routePoints = listOf(userLatLng, destLatLng)
        setCurrentRoute(routePoints)

        navigationPolyline?.remove()

        navigationPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .width(12f)
        )

        val builder = LatLngBounds.builder()
            .include(userLatLng)
            .include(destLatLng)
        val bounds = builder.build()
        val padding = 150
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))

        navigationState = NavigationState.Active
        navActionButton.text = "Stop"
        updateNavigationInfo()

        Toast.makeText(requireContext(), "Navigation started (straight line fallback).", Toast.LENGTH_SHORT).show()
    }



    // TEAM NOTE (Rushil): Stop navigation, remove route, and hide the panel.
    private fun stopNavigation() {
        navigationPolyline?.remove()
        navigationPolyline = null

        // Rushil: Clear the current route so distance/ETA fall back to default when not navigating.
        currentRoutePoints = emptyList()
        currentRouteCumulativeMeters = emptyList()

        navigationState = NavigationState.Idle
        selectedMarker = null
        selectedParkingLot = null   // Rushil: next nav can auto-pick again

        // Rushil: Stop following user when navigation ends.
        followUserDuringNav = false

        navPanelContainer.visibility = View.GONE
        navParkingContainer.visibility = View.GONE
        // Rushil: Nav panel is hidden again, so put the FAB back to its normal bottom margin.
        updateFabPositionRelativeToNavPanel()

        Toast.makeText(requireContext(), "Navigation stopped.", Toast.LENGTH_SHORT).show()
    }

    // Rushil: Given a LatLng on the map, find the closest graph node by straight-line distance.
    // This is how I "snap" the user location and the destination building to the campus path network.
    private fun findNearestNode(latLng: LatLng): NavNode? {
        val graph = campusGraph ?: return null

        var bestNode: NavNode? = null
        var bestDistMeters = Double.MAX_VALUE

        val results = FloatArray(1)

        for (node in graph.nodes) {
            Location.distanceBetween(
                latLng.latitude,
                latLng.longitude,
                node.lat,
                node.lng,
                results
            )
            val d = results[0].toDouble()
            if (d < bestDistMeters) {
                bestDistMeters = d
                bestNode = node
            }
        }

        // Rushil: If the nearest node is super far away (e.g., user is miles off campus),
        // I could reject it here. For now, I just return the nearest one and let my
        // "must be on/near campus" check decide.
        return bestNode
    }


    // Rushil: Helper to convert dp values to pixels so margins look consistent on all screens.
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // Rushil: Position the recenter FAB either at its normal bottom margin,
// or bumped up above the nav panel when the panel is visible.
    private fun updateFabPositionRelativeToNavPanel() {
        // If for some reason the FAB isn't initialized yet, just bail.
        if (!::fabRecenter.isInitialized) return

        val params = fabRecenter.layoutParams as? ViewGroup.MarginLayoutParams ?: return

        if (navPanelContainer.visibility == View.VISIBLE && navPanelHeightPx > 0) {
            // Rushil: When the nav panel is visible, move the FAB up so it sits above the panel.
            params.bottomMargin = navPanelHeightPx + dpToPx(16)
        } else {
            // Rushil: When the nav panel is hidden, use a normal bottom margin.
            params.bottomMargin = dpToPx(16)
        }

        fabRecenter.layoutParams = params
    }

    // Rushil: When I compute a new route (graph-based or straight line), I call this
// with all the polyline points in order. It precomputes the cumulative distances
// so I can quickly figure out total and remaining distance during navigation.
    private fun setCurrentRoute(points: List<LatLng>) {
        if (points.size < 2) {
            currentRoutePoints = emptyList()
            currentRouteCumulativeMeters = emptyList()
            return
        }

        currentRoutePoints = points

        val cumulative = MutableList(points.size) { 0.0 }
        var total = 0.0
        val results = FloatArray(1)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]

            Location.distanceBetween(
                prev.latitude,
                prev.longitude,
                curr.latitude,
                curr.longitude,
                results
            )
            val segment = results[0].toDouble()
            total += segment
            cumulative[i] = total
        }

        currentRouteCumulativeMeters = cumulative
    }

    // Rushil: Central place to change between WALK and CAR nav modes. Later I'll call
    // this from UI buttons (e.g., "Walk" / "Drive" chips in the nav panel).
    private fun setNavMode(mode: NavMode) {
        currentNavMode = mode

        if (mode == NavMode.WALK) {
            navParkingContainer.visibility = View.GONE
        }


        // Rushil: Whenever the mode changes during an active route, recompute ETA
        // using the same remaining distance but different speed.
        if (navigationState == NavigationState.Active) {
            updateNavigationInfo()
        }
    }

    // Rushil: Simple check so I don't try to do a car→parking→walk route
    // if the user actually tapped a parking lot as the destination.
    private fun isParkingMarker(marker: Marker?): Boolean {
        val title = marker?.title ?: return false
        // TEAM: right now I'm treating any marker with "Parking" in its title
        // as a parking destination. If I later add more detailed metadata, I
        // can tighten this check.
        return title.contains("parking", ignoreCase = true)
    }

    // Rushil: Given a path of node IDs and a node index, sum up the distance between
    // consecutive nodes in meters. Used to compare car+walk candidates for parking lots.
    private fun computePathLengthMeters(
        pathNodeIds: List<Int>,
        nodeIndex: Map<Int, NavNode>
    ): Double {
        if (pathNodeIds.size < 2) return 0.0

        var total = 0.0
        val results = FloatArray(1)

        for (i in 1 until pathNodeIds.size) {
            val a = nodeIndex[pathNodeIds[i - 1]] ?: continue
            val b = nodeIndex[pathNodeIds[i]] ?: continue

            Location.distanceBetween(
                a.lat, a.lng,
                b.lat, b.lng,
                results
            )
            total += results[0].toDouble()
        }

        return total
    }

    // Rushil: Result of a car→parking→walk multi-leg route.
// polylinePoints = full route from user → parking → destination along the graph.
// parkingLot = which lot this route uses.
    private data class MultiLegRoute(
        val polylinePoints: List<LatLng>,
        val parkingLot: ParkingLot
    )

    // Rushil: Build a multi-leg route:
//  - Car: from userLatLng to some parking lot (CAR edges).
//  - Walk: from that parking lot to destLatLng (WALK edges).
// I try all known parking lots and pick the one with the lowest total distance.
// If nothing works, I return null so the caller can fall back to a normal route.
    private fun buildCarThenWalkRoute(
        userLatLng: LatLng,
        destLatLng: LatLng,
        forcedParkingLot: ParkingLot? = null
    ): MultiLegRoute? {
        val graph = campusGraph ?: return null

        // Map nodeId -> NavNode for quick lookups.
        val nodeIndex: Map<Int, NavNode> = graph.nodes.associateBy { it.id }

        // Snap user and destination to nearest graph nodes.
        val startNode = findNearestNode(userLatLng) ?: return null
        val destNode = findNearestNode(destLatLng) ?: return null

        // Rushil: Try each parking lot, see if I can:
        //  1) drive to it (CAR edges), and
        //  2) walk from there to the building (WALK edges).
        data class Candidate(
            val parkingLot: ParkingLot,
            val carPathNodeIds: List<Int>,
            val walkPathNodeIds: List<Int>,
            val carDistanceMeters: Double,
            val walkDistanceMeters: Double,
            val totalDistanceMeters: Double
        )

        val candidates = mutableListOf<Candidate>()
        // If forcedParkingLot is set, only consider that one; otherwise try all.
        val lotsToTry: List<ParkingLot> = forcedParkingLot?.let { listOf(it) } ?: parkingLots

        for (lot in lotsToTry) {
            // Snap parking lot location to nearest graph node.
            val parkingNode = findNearestNode(lot.latLng) ?: continue

            // Car leg: user -> parking (CAR edges only).
            val carPathNodeIds = graph.shortestPathNodeIds(
                startId = startNode.id,
                endId = parkingNode.id,
                allowedModes = setOf(TravelMode.CAR)
            )
            if (carPathNodeIds.isEmpty()) {
                // Can't drive to this lot via the car network; skip it.
                continue
            }

            // Walk leg: parking -> destination (WALK edges only).
            val walkPathNodeIds = graph.shortestPathNodeIds(
                startId = parkingNode.id,
                endId = destNode.id,
                allowedModes = setOf(TravelMode.WALK)
            )
            if (walkPathNodeIds.isEmpty()) {
                // Can't walk from this lot to the building along paths; skip.
                continue
            }

            val carDist = computePathLengthMeters(carPathNodeIds, nodeIndex)
            val walkDist = computePathLengthMeters(walkPathNodeIds, nodeIndex)
            val totalDist = carDist + walkDist

            candidates.add(
                Candidate(
                    parkingLot = lot,
                    carPathNodeIds = carPathNodeIds,
                    walkPathNodeIds = walkPathNodeIds,
                    carDistanceMeters = carDist,
                    walkDistanceMeters = walkDist,
                    totalDistanceMeters = totalDist
                )
            )
        }

        if (candidates.isEmpty()) {
            // No valid car→parking→walk combo found.
            return null
        }

        // Rushil: Choose the lot with the smallest combined (car + walk) distance.
        val best = candidates.minByOrNull { it.totalDistanceMeters }!!

        // Build full polyline: userLatLng -> car leg nodes -> walk leg nodes -> destLatLng.
        val points = mutableListOf<LatLng>()

        // Start exactly at the user's location.
        points.add(userLatLng)

        // Append car leg nodes along the graph.
        for (nodeId in best.carPathNodeIds) {
            val node = nodeIndex[nodeId] ?: continue
            points.add(LatLng(node.lat, node.lng))
        }

        // Append walk leg nodes, skipping the first if it's the same as the last car node.
        if (best.walkPathNodeIds.isNotEmpty()) {
            val carLast = best.carPathNodeIds.lastOrNull()
            val walkIds = best.walkPathNodeIds

            val startIndex = if (carLast != null && walkIds.first() == carLast) 1 else 0

            for (i in startIndex until walkIds.size) {
                val node = nodeIndex[walkIds[i]] ?: continue
                points.add(LatLng(node.lat, node.lng))
            }
        }

        // End exactly at the destination marker.
        points.add(destLatLng)

        return MultiLegRoute(
            polylinePoints = points,
            parkingLot = best.parkingLot
        )
    }

    // Rushil: Let the user manually choose which parking lot to use for car navigation.
// When they pick one, I recalc the multi-leg route from their current location.
    private fun showParkingLotChooser() {
        val lots = parkingLots
        if (lots.isEmpty()) return

        val names = lots.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose parking lot")
            .setItems(names) { _, which ->
                val chosen = lots[which]
                selectedParkingLot = chosen
                navParkingText.text = "Parking: ${chosen.name}"

                // If navigation is active, immediately recompute the route from current position.
                if (navigationState == NavigationState.Active) {
                    // This will re-use selectedParkingLot when building the route.
                    startNavigationFromCurrentLocation()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
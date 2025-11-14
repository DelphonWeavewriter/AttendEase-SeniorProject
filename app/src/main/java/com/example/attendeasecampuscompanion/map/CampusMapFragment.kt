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
import android.graphics.Color
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.CameraPosition
import android.widget.ImageView
import kotlin.math.absoluteValue










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

    private lateinit var navIParkedButton: Button



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

    // Rushil: Nodes that are connected to at least one CAR edge in the graph.
    // I use these to snap the start of the drive leg to the nearest road node
    // instead of whatever walkway node I'm standing on.
    private var carCapableNodeIds: Set<Int> = emptySet()


    // Rushil: While this is true, the camera will "follow" my location during navigation.
    // Once I manually drag the map, I'll set this to false so the camera stops snapping back.
    private var followUserDuringNav: Boolean = false

    // Rushil: Track last camera target + bearing so I can rotate the map
    // to face the direction I'm actually moving while in live nav.
    private var lastNavLatLng: LatLng? = null
    private var lastNavBearingDeg: Float = 0f

    // Rushil: Slight tilt for a more "nav-like" 3D view. Tweak or set to 0f if you prefer flat.
    private val navCameraTiltDeg: Float = 45f

    // Rushil: Heading behavior tuning.
    // If I move less than this between updates, assume I'm "not really moving".
    private val MIN_MOVEMENT_FOR_HEADING_METERS = 3.0



    // Max change in bearing per update when smoothing.
    private val MAX_BEARING_STEP_DEG = 45f



    // Rushil: I'll keep a reference to the recenter FAB so I can move it when the nav panel is visible.
    private lateinit var fabRecenter: FloatingActionButton

    // Rushil: Cache of the nav panel's height in pixels so I can position the FAB just above it.
    private var navPanelHeightPx: Int = 0



    // Rushil: Finer-grained nav states so I can separate route preview vs live nav.
    private enum class NavigationState {
        Idle,           // No destination selected
        Preview,        // Marker selected, no route yet, button = "Navigate"
        RoutePreview,   // Route drawn (full path), waiting for user to press "Start"
        Active          // Live navigation (camera follow, I parked, etc.)
    }

    // Rushil: If null, I'll auto-pick the best lot. If non-null,
    // I route specifically via this lot for car→parking→walk.
    private var selectedParkingLot: ParkingLot? = null

    // Rushil: Speeds in meters per second for ETA calculations.
    // ~1.4 m/s ≈ 5 km/h (normal walking pace).
    private val walkingSpeedMetersPerSec = 1.4

    // Rushil: Off-route rerouting thresholds.
    // If I'm more than ~25m off the route, I'll trigger a recalculation.
    private val REROUTE_DISTANCE_THRESHOLD_METERS = 25.0

    // Rushil: Don't spam reroutes; enforce a minimum gap between recalculations.
    private val REROUTE_MIN_TIME_BETWEEN_MS = 10_000L  // 10 seconds

    private var lastRerouteTimeMillis: Long = 0L


    // Rushil: Campus-safe driving speed. You can tweak between 4–8 m/s depending
    // on how optimistic you want ETAs to be. 5 m/s ≈ 18 km/h (~11 mph).
    private val drivingSpeedMetersPerSec = 5.0

    // Rushil: Camera presets for navigation.
    private val NAV_ZOOM_WALK = 18f
    private val NAV_ZOOM_CAR  = 17f
    private val NAV_TILT_DEGREES = 45f

    // Rushil: Don't rotate camera if we're basically standing still.
    private val MIN_SPEED_FOR_HEADING_MPS = 0.5f  // ~1.1 mph

    // Rushil: Remember last used nav bearing so we don't snap back to 0 when GPS has no heading.
    private var lastNavCameraBearing: Float = 0f



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

    /// Rushil: Main route polyline (car leg in multi-leg, or full route in single-leg).
    private var navigationPolyline: Polyline? = null

    // Rushil: Optional second polyline for the walking leg in a car→parking→walk route.
    // This lets me style walk differently (e.g., dashed) without breaking the car line.
    private var navigationPolylineWalk: Polyline? = null

    // TEAM NOTE (Rushil): Last known user location (updated from location callbacks).
    private var lastKnownLocation: Location? = null

    // Rushil: Store the full polyline of the current route (user → destination).
    // This includes the start point, all graph nodes, and the final destination point.
    private var currentRoutePoints: List<LatLng> = emptyList()

    // Rushil: If I'm on a car→parking→walk route, keep the full multi-leg
    // object so the "I parked" button can switch to the walking leg only.
    private var activeMultiLegRoute: MultiLegRoute? = null


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

    // Rushil: Top banner for the next turn (replaces search bar during live nav).
    private lateinit var navNextTurnContainer: View
    private lateinit var navNextTurnIcon: android.widget.ImageView
    private lateinit var navNextTurnText: TextView



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

    // Rushil: Simple convenience to measure distance between two LatLngs in meters.
    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            results
        )
        return results[0].toDouble()
    }





    // Rushil: Compute the compass bearing (0–360°) from one LatLng to another.
    private fun bearingBetween(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val dLon = lon2 - lon1
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)

        var brng = Math.toDegrees(Math.atan2(y, x))
        // Normalize to 0–360
        brng = (brng + 360.0) % 360.0
        return brng.toFloat()
    }

    // Rushil: Smooth the bearing so the camera doesn't jerk around on small GPS noise.
    // Rushil: Smooth the bearing so the camera doesn't jerk on noise, but still
    // feels snappy when I actually turn. maxStep controls how fast it can swing.
    private fun smoothBearing(prev: Float, new: Float, maxStep: Float = MAX_BEARING_STEP_DEG): Float {
        // Wrap difference into [-180, +180]
        var diff = ((new - prev + 540f) % 360f) - 180f

        // Clamp to maxStep so we don't spin too fast from tiny changes.
        if (diff > maxStep) diff = maxStep
        if (diff < -maxStep) diff = -maxStep

        var out = prev + diff

        // Normalize back to 0–360
        if (out < 0f) out += 360f
        if (out >= 360f) out -= 360f

        return out
    }


    // Rushil: Smallest absolute difference between two bearings (0..180).
    private fun smallestAngleDiff(a: Float, b: Float): Float {
        var diff = Math.abs(a - b) % 360f
        if (diff > 180f) diff = 360f - diff
        return diff
    }

    // Rushil: Short distance formatter (e.g. "200 ft" or "0.2 mi").
    private fun formatDistanceShort(meters: Double): String {
        val miles = meters * 0.000621371
        return if (miles < 0.1) {
            val feet = meters * 3.28084
            "${feet.toInt()} ft"
        } else {
            "%.1f mi".format(miles)
        }
    }
    // Rushil: Update the "Next: ..." line based on current location and route geometry.
    private fun updateNextTurnInstruction(userLatLng: LatLng) {
        if (!::navNextTurnText.isInitialized || !::navNextTurnIcon.isInitialized) return

        if (currentRoutePoints.size < 3 ||
            currentRouteCumulativeMeters.size != currentRoutePoints.size) {
            navNextTurnText.text = "Next: —"
            navNextTurnIcon.setImageResource(R.drawable.ic_continue_straight)
            return
        }

        val results = FloatArray(1)

        // 1) Find nearest route vertex to user.
        var bestIndex = 0
        var bestDistToVertex = Double.MAX_VALUE
        for (i in currentRoutePoints.indices) {
            val pt = currentRoutePoints[i]
            Location.distanceBetween(
                userLatLng.latitude, userLatLng.longitude,
                pt.latitude, pt.longitude,
                results
            )
            val d = results[0].toDouble()
            if (d < bestDistToVertex) {
                bestDistToVertex = d
                bestIndex = i
            }
        }

        // 2) Look ahead for the first "real" turn (bearing change past threshold).
        val TURN_ANGLE_DEG = 35f

        var turnIndex = -1
        val startI = maxOf(bestIndex, 1)  // need i-1, i, i+1

        for (i in startI until currentRoutePoints.size - 1) {
            val prev = currentRoutePoints[i - 1]
            val cur  = currentRoutePoints[i]
            val next = currentRoutePoints[i + 1]

            val bIn  = bearingBetween(prev, cur)
            val bOut = bearingBetween(cur, next)
            val diff = smallestAngleDiff(bIn, bOut)

            if (diff >= TURN_ANGLE_DEG) {
                turnIndex = i
                break
            }
        }

        if (turnIndex == -1) {
            // No more big turns ahead; just continue to destination.
            navNextTurnText.text = "Next: Continue straight to destination"
            return
        }

        // 3) Distance from user to that turn along the route.
        // Distance user → nearest vertex:
        Location.distanceBetween(
            userLatLng.latitude, userLatLng.longitude,
            currentRoutePoints[bestIndex].latitude, currentRoutePoints[bestIndex].longitude,
            results
        )
        val distUserToBest = results[0].toDouble()

        val remainingAlongRoute =
            currentRouteCumulativeMeters[turnIndex] - currentRouteCumulativeMeters[bestIndex]

        val distToTurn = distUserToBest + remainingAlongRoute

        // 4) Decide left/right vs bear left/right.
        val prev = currentRoutePoints[turnIndex - 1]
        val cur  = currentRoutePoints[turnIndex]
        val next = currentRoutePoints[turnIndex + 1]

        val bIn  = bearingBetween(prev, cur)
        val bOut = bearingBetween(cur, next)

        // Signed diff in -180..+180 (positive = right turn).
        val rawDiff = (((bOut - bIn) + 540f) % 360f) - 180f
        val absDiff = kotlin.math.abs(rawDiff)

        val dirWord = when {
            absDiff < 35f -> "continue straight"
            rawDiff > 0   -> if (absDiff > 100f) "turn right" else "bear right"
            else          -> if (absDiff > 100f) "turn left"  else "bear left"
        }

        val distText = formatDistanceShort(distToTurn)

        // Choose an icon based on the type of turn.
        val iconRes = when {
            dirWord.startsWith("turn left")      -> R.drawable.ic_turn_left
            dirWord.startsWith("turn right")     -> R.drawable.ic_turn_right
            dirWord.startsWith("bear left")      -> R.drawable.ic_bear_left
            dirWord.startsWith("bear right")     -> R.drawable.ic_bear_right
            else                                 -> R.drawable.ic_continue_straight
        }

        navNextTurnIcon.setImageResource(iconRes)
        navNextTurnText.text = "Next: $dirWord in $distText"

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

        // First try prefix matches
        val prefixMatches = buildingPlaces.filter {
            it.title.lowercase().startsWith(q)
        }

        // If no prefix matches, fall back to "contains" matches
        val results = if (prefixMatches.isNotEmpty()) {
            prefixMatches
        } else {
            buildingPlaces.filter {
                it.title.lowercase().contains(q)
            }
        }.take(10)

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
        // Rushil: Top next-turn banner (used only during live navigation).
        navNextTurnContainer = root.findViewById(R.id.navNextTurnContainer)
        navNextTurnIcon = root.findViewById(R.id.navNextTurnIcon)
        navNextTurnText = root.findViewById(R.id.navNextTurnText)
        navNextTurnContainer.visibility = View.GONE


        navNextTurnText = root.findViewById(R.id.navNextTurnText)
        navNextTurnContainer.visibility = View.GONE

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



        //  Parking controls
        navParkingContainer = root.findViewById(R.id.navParkingContainer)
        navParkingText = root.findViewById(R.id.navParkingText)
        navChangeParkingButton = root.findViewById(R.id.navChangeParkingButton)
        navIParkedButton = root.findViewById(R.id.navIParkedButton)

        // Hide by default; only show in CAR mode with a multi-leg route.
        navParkingContainer.visibility = View.GONE
        navIParkedButton.visibility = View.GONE

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
        navIParkedButton.setOnClickListener {
            val multi = activeMultiLegRoute
            val googleMap = map

            if (multi == null || googleMap == null) {
                Toast.makeText(requireContext(), "No active car + walk route.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val walkPoints = multi.walkLegPoints
            if (walkPoints.size < 2) {
                // Nothing meaningful to draw; just hide parking UI.
                activeMultiLegRoute = null
                navParkingContainer.visibility = View.GONE
                navIParkedButton.visibility = View.GONE
                return@setOnClickListener
            }

            // 1) Treat the walk leg as the *entire* route from now on.
            setCurrentRoute(walkPoints)

            // 2) Replace old polylines: remove car + old walk polyline.
            navigationPolyline?.remove()
            navigationPolylineWalk?.remove()
            navigationPolyline = null
            navigationPolylineWalk = null

            // 3) Draw a fresh, single-leg dotted purple line for walking.
            val dashPattern: List<PatternItem> = listOf(
                Dot(),
                Gap(20f)
            )

            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(walkPoints)
                    .width(12f)
                    .color(Color.MAGENTA)   // walking leg = purple
                    .pattern(dashPattern)
            )

            // 4) We are no longer multi-leg. From this point on, the trim logic
            //     will apply Case 2 (single-leg walk) and shrink this line.
            activeMultiLegRoute = null

            // 5) Hide parking UI since I'm now on foot only.
            navParkingContainer.visibility = View.GONE
            navIParkedButton.visibility = View.GONE

            // 6) Logically switch to WALK mode and sync the UI toggle.
            currentNavMode = NavMode.WALK
            navModeToggleGroup.check(R.id.btnModeWalk)

            // 7) Reset heading tracking so the camera heading restarts clean.
            lastNavLatLng = null

            Toast.makeText(requireContext(), "Switched to walking route from parked car.", Toast.LENGTH_SHORT).show()
        }



        // Rushil: Handle clicks on the main "Navigate / Stop" button.
        navActionButton.setOnClickListener {
            when (navigationState) {
                NavigationState.Preview -> {
                    // Rushil: First tap → build full route preview (show the whole line).
                    buildRoutePreviewFromCurrentLocation()
                }

                NavigationState.RoutePreview -> {
                    // Rushil: Second tap → actually start live navigation.
                    beginLiveNavigationFromPreview()
                }

                NavigationState.Active -> {
                    // Rushil: I'm currently navigating and tapped "Stop" → end navigation.
                    stopNavigation()
                }

                NavigationState.Idle -> {
                    Toast.makeText(
                        requireContext(),
                        "Select a destination first.",
                        Toast.LENGTH_SHORT
                    ).show()
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
        // Rushil: Start in Idle with mode toggle enabled.
        updateNavModeToggleEnabled()



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
            // Rushil: Precompute which graph nodes are part of at least one CAR edge.
            campusGraph?.let { graph ->
                val carNodes = mutableSetOf<Int>()
                for (edge in graph.edges) {
                    if (TravelMode.CAR in edge.modes) {
                        carNodes.add(edge.fromId)
                        carNodes.add(edge.toId)
                    }
                }
                carCapableNodeIds = carNodes
                android.util.Log.d(
                    "CampusMap",
                    "Car-capable nodes: ${carCapableNodeIds.size}"
                )
            }

        } catch (e: Exception) {
            // Rushil: If the file is missing or JSON is bad, don't crash the app.
            campusGraph = null
            android.util.Log.e("CampusMap", "Failed to load campus graph JSON", e)
        }
        // ================== END GRAPH LOAD ==================
        setSearchUiVisible(true)

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


        // ===== Manual fallback markers for locations that may be missing from OSM =====
        // Rushil: These make sure these places ALWAYS show up in the search list,
        // even if the Overpass → polygon logic fails to find them. They are hidden
        // by default and only appear when selected from the search bar.

        // --- Other campus buildings ---

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.80803, -73.60538)) // Sculpture Barn (Mapcarta)
                .title("Sculpture Barn")
                .snippet("Campus building")
                .icon(buildingIcon)
        )

        // NOTE: This is a placeholder near the core campus.
        // Rushil: Once you have exact coords, just update this LatLng.
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.814377152585266, -73.60602636790536))
                .title("North House")
                .snippet("Campus building")
                .icon(buildingIcon)
        )

        // 500 Building / BRIIC is just south/east of Rockefeller on campus.
        // These coords are approximate; tweak visually if needed.
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.809816765998825, -73.60658995687632))
                .title("Biomedical Research, Innovation, and Imaging Center (500 Building)")
                .snippet("Medicine & Health Sciences")
                .icon(buildingIcon)
        )

        // Maintenance Barn (service area near Rec / fields)
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.81202917589616, -73.60131250814656)) // Mapcarta
                .title("Maintenance Barn")
                .snippet("Activities/Recreation")
                .icon(buildingIcon)
        )

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.811290439189634, -73.60097759637884)) // Recreation Hall (Mapcarta)
                .title("Recreation Hall")
                .snippet("Activities/Recreation")
                .icon(buildingIcon)
        )

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.81143537638618, -73.60074694677432)) // Food Service (Mapcarta)
                .title("Food Service")
                .snippet("Activities/Recreation")
                .icon(buildingIcon)
        )

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.81026120867593, -73.5996916509864)) // Green Lodge (Mapcarta)
                .title("Green Lodge")
                .snippet("Activities/Recreation")
                .icon(buildingIcon)
        )

        // Balding House appears in the same cluster as Green Lodge / Food Service.
        // This is an approximate on-campus position for now.
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.810381799280634, -73.59892266623883)) // TODO: refine for Balding House
                .title("Balding House")
                .snippet("Activities/Recreation")
                .icon(buildingIcon)
        )

        // Gerry House & Tower House sit in the Rockefeller / Riland / Tower cluster.
        // I’m centering them near Rockefeller and offsetting a bit so they’re distinct.
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.81255110688574, -73.60746904536539)) // TODO: refine for Gerry House
                .title("Gerry House")
                .snippet("Campus building")
                .icon(buildingIcon)
        )

        // Mapcarta says Tower House is ~360 ft northwest of Rockefeller Hall.
        // This LatLng approximates that offset on campus.
        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.81100, -73.60713)) // approx.  Tower House; tweak if needed
                .title("Tower House")
                .snippet("Campus building")
                .icon(buildingIcon)
        )

        // --- Athletics: fields / diamonds ---

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.80942090457003, -73.60188929736317)) // Angelo Lorenzo Memorial Baseball Field (Mapcarta)
                .title("Angelo Lorenzo Memorial Baseball Field")
                .snippet("Athletics")
                .icon(buildingIcon)
        )

        addBuildingMarkerKeepRef(
            MarkerOptions()
                .position(LatLng(40.80838, -73.60264)) // NYIT Softball Complex (Mapcarta)
                .title("NYIT Softball Complex")
                .snippet("Athletics")
                .icon(buildingIcon)
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
        // Rushil: If I'm just previewing (with or without route), tapping empty map cancels nav.
        googleMap.setOnMapClickListener {
            if (navigationState == NavigationState.Preview ||
                navigationState == NavigationState.RoutePreview
            ) {
                cancelNavigationPreview()
            }
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
                updateNavCamera(location)
            }

            // Rushil: Visually trim the route so only the remaining part is drawn.
            updateRoutePolylineForProgress(location)

            // Reroute if I'm way off.
            maybeRecalculateRoute(location)
        } else if (navigationState == NavigationState.Preview ||
            navigationState == NavigationState.RoutePreview) {
            updateNavigationInfo()
        }
    }


    // Rushil: Called from onUserLocationUpdated while Active. If I'm significantly off
    // the current route, rebuild the route from my current location to the same destination.
    private fun maybeRecalculateRoute(location: Location) {
        if (navigationState != NavigationState.Active) return
        val marker = selectedMarker ?: return       // no destination
        if (currentRoutePoints.size < 2) return     // no route to compare

        val now = System.currentTimeMillis()
        if (now - lastRerouteTimeMillis < REROUTE_MIN_TIME_BETWEEN_MS) {
            // Rushil: Too soon since last reroute; skip this check.
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)
        val distToRoute = distanceFromUserToRoute(userLatLng)

        if (distToRoute < REROUTE_DISTANCE_THRESHOLD_METERS) {
            // Rushil: Still close enough to the planned route; no reroute needed.
            return
        }

        // At this point I'm clearly off-route.
        lastRerouteTimeMillis = now
        Toast.makeText(requireContext(), "Rerouting...", Toast.LENGTH_SHORT).show()

        recalculateActiveRouteFromCurrentLocation()
    }





    // Rushil: Center the camera on the user and rotate/tilt it so "up" on the map
    // matches the direction I'm moving in during live navigation.
    private fun updateNavCamera(location: Location) {
        val googleMap = map ?: return

        val userLatLng = LatLng(location.latitude, location.longitude)
        val prevLatLng = lastNavLatLng

        val currentZoom = googleMap.cameraPosition.zoom.takeIf { !it.isNaN() } ?: 18f

        // --- 1) If this is our very first nav update, just center without rotating. ---
        if (prevLatLng == null) {
            lastNavLatLng = userLatLng
            // Keep north-up or whatever the previous bearing was.
            val initialBearing = googleMap.cameraPosition.bearing

            lastNavBearingDeg = initialBearing

            val firstCamPos = com.google.android.gms.maps.model.CameraPosition.Builder()
                .target(userLatLng)
                .zoom(currentZoom)
                .bearing(initialBearing)
                .tilt(navCameraTiltDeg)
                .build()

            googleMap.animateCamera(
                com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(firstCamPos)
            )
            return
        }

        // --- 2) Compute how far we actually moved since last update. ---
        val results = FloatArray(1)
        Location.distanceBetween(
            prevLatLng.latitude, prevLatLng.longitude,
            userLatLng.latitude, userLatLng.longitude,
            results
        )
        val movedMeters = results[0].toDouble()
        val speedMps = location.speed   // may be 0 or noisy, but good as an extra hint.

        val shouldUpdateBearing =
            movedMeters >= MIN_MOVEMENT_FOR_HEADING_METERS &&
                    speedMps >= MIN_SPEED_FOR_HEADING_MPS

        val targetBearing: Float = if (shouldUpdateBearing) {
            // I'm actually moving – compute new bearing from previous position to current.
            val rawBearing = bearingBetween(prevLatLng, userLatLng)

            // If the turn is huge (e.g. I actually turned a corner), allow a big jump.
            val prev = lastNavBearingDeg
            val rawDiff = (((rawBearing - prev + 540f) % 360f) - 180f).absoluteValue

            if (rawDiff > 90f) {
                // Big direction change → snap to it.
                rawBearing
            } else {
                // Normal movement → smooth it.
                smoothBearing(prev, rawBearing)
            }
        } else {
            // Not really moving → keep last bearing, don't rotate.
            lastNavBearingDeg
        }

        lastNavLatLng = userLatLng
        lastNavBearingDeg = targetBearing

        val camPos = com.google.android.gms.maps.model.CameraPosition.Builder()
            .target(userLatLng)
            .zoom(currentZoom)
            .bearing(targetBearing)
            .tilt(navCameraTiltDeg)
            .build()

        googleMap.animateCamera(
            com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(camPos)
        )
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
            loc?.let { location ->
                lastKnownLocation = location

                if (navigationState == NavigationState.Active) {
                    // Rushil: In live nav, recenter should jump back into nav POV and re-enable follow.
                    followUserDuringNav = true
                    updateNavCamera(location)
                } else {
                    // Rushil: Outside of Active nav, use a simple center + zoom.
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            17f
                        )
                    )
                }
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

        // Make sure I parked is **not** visible just for picking a marker.
        navParkingContainer.visibility = View.GONE
        navIParkedButton.visibility = View.GONE


        // Rushil: Mode can still be changed at this stage.
        updateNavModeToggleEnabled()
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
            navDistanceText.text = "Distance: (waiting for GPS...)"
            navEtaText.text = "ETA: —"
            navArrivalTimeText.text = "Arrival: —"
            return
        }

        val userLatLng = LatLng(location.latitude, location.longitude)
        val destLatLng = marker.position

        val distanceMeters: Double

        if (currentRoutePoints.size >= 2 && currentRouteCumulativeMeters.size == currentRoutePoints.size) {
            val results = FloatArray(1)

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

            val remainingFromVertex = currentRouteTotalMeters - currentRouteCumulativeMeters[bestIndex]
            distanceMeters = bestDistToVertex + remainingFromVertex
        } else {
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

        val speedMetersPerSec = when (currentNavMode) {
            NavMode.WALK -> walkingSpeedMetersPerSec
            NavMode.CAR  -> drivingSpeedMetersPerSec
        }

        val etaSeconds = distanceMeters / speedMetersPerSec
        val etaMinutes = (etaSeconds / 60.0)

        val miles = distanceMeters * 0.000621371
        val distanceText = if (miles < 0.1) {
            val feet = distanceMeters * 3.28084
            "Distance: ${feet.toInt()} ft"
        } else {
            "Distance: %.2f mi".format(miles)
        }
        navDistanceText.text = distanceText

        val etaText = if (etaMinutes < 1.0) {
            "ETA: < 1 min"
        } else {
            "ETA: ${etaMinutes.toInt()} min"
        }
        navEtaText.text = etaText

        val etaMillis = (etaSeconds * 1000).toLong()
        val arrivalTimeMillis = System.currentTimeMillis() + etaMillis
        val arrivalDate = Date(arrivalTimeMillis)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        navArrivalTimeText.text = "Arrival: ${timeFormat.format(arrivalDate)}"


        if (navigationState == NavigationState.Active && currentRoutePoints.size >= 2) {
            updateNextTurnInstruction(userLatLng)
        } else {
            if (::navNextTurnText.isInitialized) {
                navNextTurnText.text = "Next: —"
                if (::navNextTurnIcon.isInitialized) {
                    navNextTurnIcon.setImageResource(R.drawable.ic_continue_straight)
                }
            }
        }
    }









    // Rushil: Called when I tap "Navigate" in preview mode.
    // Phase 3: Try to use the campus path graph to route along walkways; if that fails, fall back to straight line.
    private fun buildRoutePreviewFromCurrentLocation() {
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
        var multiLegRouteUsed: MultiLegRoute? = null  // Rushil: keep a handle if we used car→parking→walk


        if (shouldUseMultiLegCar) {
            // Rushil: Try to build a car→parking→walk route.
            // If selectedParkingLot is set, force that lot. Otherwise auto-choose best.
            val multiLeg = buildCarThenWalkRoute(
                userLatLng = userLatLng,
                destLatLng = destLatLng,
                forcedParkingLot = selectedParkingLot
            )

            if (multiLeg != null) {
                // Rushil: Remember the multi-leg route for drawing + "I parked".
                multiLegRouteUsed = multiLeg
                activeMultiLegRoute = multiLeg

                finalPolylinePoints = multiLeg.fullRoutePoints
                selectedParkingLot = multiLeg.parkingLot
                viaParkingLotName = multiLeg.parkingLot.name
            } else {
                activeMultiLegRoute = null
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

        // Clear any existing route polylines.
        navigationPolyline?.remove()
        navigationPolylineWalk?.remove()
        navigationPolyline = null
        navigationPolylineWalk = null

        if (shouldUseMultiLegCar && multiLegRouteUsed != null) {
            // Rushil: In car mode with a multi-leg route, draw car and walk segments differently.

            // Car leg: solid blue line.
            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(multiLegRouteUsed!!.carLegPoints)
                    .width(12f)
                    .color(Color.BLUE)
            )

            // Walk leg: dashed purple line.
            val dashPattern: List<PatternItem> = listOf(
                Dot(),
                Gap(20f)
            )

            navigationPolylineWalk = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(multiLegRouteUsed!!.walkLegPoints)
                    .width(10f)
                    .color(Color.MAGENTA)
                    .pattern(dashPattern)
            )
        } else {
            // Rushil: Single-leg route (walk mode or straight-line fallback).
            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(finalPolylinePoints)
                    .width(12f)
                    .color(Color.BLUE)
            )
        }

        // Fit camera around user + destination (route is inside this box).
        val builder = LatLngBounds.builder()
            .include(userLatLng)
            .include(destLatLng)
        val bounds = builder.build()
        val padding = 150
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))


        navigationState = NavigationState.RoutePreview
        navActionButton.text = "Start"
        followUserDuringNav = false
        updateNavModeToggleEnabled()



        // During preview: show which parking lot will be used, but NOT "I parked".
        if (shouldUseMultiLegCar && selectedParkingLot != null && activeMultiLegRoute != null) {
            navParkingContainer.visibility = View.VISIBLE
            navParkingText.text = "Parking: ${selectedParkingLot?.name}"
            navIParkedButton.visibility = View.GONE
        } else {
            navParkingContainer.visibility = View.GONE
            navIParkedButton.visibility = View.GONE
        }

        // Keep distance/ETA updated in the panel.
        updateNavigationInfo()

        if (viaParkingLotName != null) {
            Toast.makeText(
                requireContext(),
                "Route ready: drive to $viaParkingLotName, then walk to ${marker.title}. Press Start to begin.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                "Route ready along campus paths. Press Start to begin navigation.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Rushil: Once the route is previewed and looks good, this actually starts live nav:
// camera follow, turn-by-turn, I parked, etc.
    private fun beginLiveNavigationFromPreview() {
        val googleMap = map ?: run {
            Toast.makeText(requireContext(), "Map is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }

        if (navigationState != NavigationState.RoutePreview) {
            Toast.makeText(requireContext(), "Preview a route first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentRoutePoints.isEmpty()) {
            Toast.makeText(requireContext(), "No route to start. Try navigating again.", Toast.LENGTH_SHORT).show()
            return
        }

        // Live nav uses the same route we already computed.
        navigationState = NavigationState.Active
        navActionButton.text = "Stop"
        followUserDuringNav = true
        // Rushil: Reset heading tracking for fresh nav session.
        lastNavLatLng = null
        lastNavBearingDeg = 0f
        updateNavModeToggleEnabled()
        // Rushil: Live nav → hide search, show next-turn line.
        setSearchUiVisible(false)
        navNextTurnContainer.visibility = View.VISIBLE



        //  Only now (during Active nav) should the I parked button be available.
        if (currentNavMode == NavMode.CAR && activeMultiLegRoute != null && selectedParkingLot != null) {
            navParkingContainer.visibility = View.VISIBLE
            navParkingText.text = "Parking: ${selectedParkingLot?.name}"
            navIParkedButton.visibility = View.VISIBLE
        } else {
            navParkingContainer.visibility = View.GONE
            navIParkedButton.visibility = View.GONE
        }

        // Rushil: When I press Start, immediately snap into nav camera POV if I have a location.
        lastKnownLocation?.let { loc ->
            updateNavCamera(loc)
        }

        // Refresh distance/ETA in case user has already moved since preview.
        updateNavigationInfo()

        Toast.makeText(
            requireContext(),
            "Navigation started.",
            Toast.LENGTH_SHORT
        ).show()
    }




    // Rushil: Rebuild the current live route from my *current* GPS location to the
    // same selected marker, keeping nav in Active state. Used for off-route rerouting.
    private fun recalculateActiveRouteFromCurrentLocation() {
        val marker = selectedMarker ?: return
        val location = lastKnownLocation ?: return
        val googleMap = map ?: return

        val userLatLng = LatLng(location.latitude, location.longitude)
        val destLatLng = marker.position

        val graph = campusGraph
        if (graph == null) {
            // Rushil: If graph failed, just switch to a new straight line.
            startStraightLineNavigation(userLatLng, destLatLng)
            return
        }

        // Decide if we should try multi-leg routing:
        //  - only in CAR mode
        //  - only if the destination is NOT itself a parking lot
        val shouldUseMultiLegCar = (currentNavMode == NavMode.CAR) && !isParkingMarker(marker)

        val finalPolylinePoints: List<LatLng>
        var viaParkingLotName: String? = null
        var multiLegRouteUsed: MultiLegRoute? = null  // Rushil: keep a handle if we used car→parking→walk

        if (shouldUseMultiLegCar) {
            // Rushil: Try to build a car→parking→walk route.
            val multiLeg = buildCarThenWalkRoute(
                userLatLng = userLatLng,
                destLatLng = destLatLng,
                forcedParkingLot = selectedParkingLot
            )

            if (multiLeg != null) {
                multiLegRouteUsed = multiLeg
                activeMultiLegRoute = multiLeg

                finalPolylinePoints = multiLeg.fullRoutePoints
                selectedParkingLot = multiLeg.parkingLot
                viaParkingLotName = multiLeg.parkingLot.name
            } else {
                activeMultiLegRoute = null

                // Fallback: pure walking route along the graph.
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
            // Rushil: WALK mode, or destination is already a parking lot → single-leg walking route.
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
            activeMultiLegRoute = null
        }

        // Rushil: Save this route so remaining-distance/ETA & turn guidance use the actual path.
        setCurrentRoute(finalPolylinePoints)

        // Clear any existing route polylines.
        navigationPolyline?.remove()
        navigationPolylineWalk?.remove()
        navigationPolyline = null
        navigationPolylineWalk = null

        // ---- Draw updated polylines with proper styling ----
        navigationPolyline?.remove()
        navigationPolylineWalk?.remove()
        navigationPolyline = null
        navigationPolylineWalk = null

        if (shouldUseMultiLegCar && multiLegRouteUsed != null) {
            // ================== Multi-leg: CAR + WALK ==================

            // Car leg: solid blue line.
            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(multiLegRouteUsed.carLegPoints)
                    .width(12f)
                    .color(Color.BLUE)
            )

            // Walk leg: dotted purple line.
            val dashPattern: List<PatternItem> = listOf(
                Dot(),
                Gap(20f)
            )

            navigationPolylineWalk = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(multiLegRouteUsed.walkLegPoints)
                    .width(10f)
                    .color(Color.MAGENTA)
                    .pattern(dashPattern)
            )
        } else {
            // ================== Single-leg WALK route ==================
            // (Either pure WALK mode, or CAR mode where we fell back to walking only.)

            activeMultiLegRoute = null

            val dashPattern: List<PatternItem> = listOf(
                Dot(),
                Gap(20f)
            )

            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(finalPolylinePoints)
                    .width(12f)
                    .color(Color.MAGENTA)
                    .pattern(dashPattern)
            )
        }

        // Update parking row visibility / text.
        if (shouldUseMultiLegCar && selectedParkingLot != null && activeMultiLegRoute != null) {
            navParkingContainer.visibility = View.VISIBLE
            navParkingText.text = "Parking: ${selectedParkingLot?.name}"
            navIParkedButton.visibility = View.VISIBLE
        } else {
            navParkingContainer.visibility = View.GONE
            navIParkedButton.visibility = View.GONE
        }

        // Refresh stats and next-turn after reroute.
        updateNavigationInfo()

        // Rushil: Keep camera behavior the same; updateNavCamera() will continue to follow.
        // We don't need to refit bounds here.

        // Update parking row visibility / text.
        if (shouldUseMultiLegCar && selectedParkingLot != null && activeMultiLegRoute != null) {
            navParkingContainer.visibility = View.VISIBLE
            navParkingText.text = "Parking: ${selectedParkingLot?.name}"
            navIParkedButton.visibility = View.VISIBLE
        } else {
            navParkingContainer.visibility = View.GONE
            navIParkedButton.visibility = View.GONE
        }

        // Refresh stats and next-turn after reroute.
        updateNavigationInfo()
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

        // Rushil: Clear any existing route polylines from a previous nav session.
        navigationPolyline?.remove()
        navigationPolylineWalk?.remove()
        navigationPolyline = null
        navigationPolylineWalk = null

        navigationPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(routePoints)
                .width(12f)
                .color(Color.BLUE) // TEAM: straight-line fallback uses same style as car
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
        navigationPolylineWalk?.remove()
        navigationPolylineWalk = null

        // Rushil: Clear the current route so distance/ETA fall back to default when not navigating.
        currentRoutePoints = emptyList()
        currentRouteCumulativeMeters = emptyList()

        navigationState = NavigationState.Idle
        selectedMarker = null
        selectedParkingLot = null   // Rushil: next nav can auto-pick again
        activeMultiLegRoute = null
        updateNavModeToggleEnabled()


        // Rushil: Stop following user when navigation ends.
        followUserDuringNav = false

        // Rushil: Clear heading tracking as well so next nav starts clean.
        lastNavLatLng = null
        lastNavBearingDeg = 0f

        navPanelContainer.visibility = View.GONE
        navParkingContainer.visibility = View.GONE
        navIParkedButton.visibility = View.GONE
        // Rushil: Nav panel is hidden again, so put the FAB back to its normal bottom margin.
        updateFabPositionRelativeToNavPanel()

        Toast.makeText(requireContext(), "Navigation stopped.", Toast.LENGTH_SHORT).show()
        // Rushil: Leaving nav → show search again, hide next-turn.
        setSearchUiVisible(true)
        navNextTurnContainer.visibility = View.GONE

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


    // Rushil: Like findNearestNode, but only considers nodes that can be used for CAR edges.
    // This lets me snap the start of the drive leg to the closest road node, even if my
    // actual GPS location is sitting on a walkway node.
    private fun findNearestCarCapableNode(latLng: LatLng): NavNode? {
        val graph = campusGraph ?: return null
        if (carCapableNodeIds.isEmpty()) return null

        var bestNode: NavNode? = null
        var bestDistMeters = Double.MAX_VALUE
        val results = FloatArray(1)

        for (node in graph.nodes) {
            if (!carCapableNodeIds.contains(node.id)) continue

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

        return bestNode
    }



    // Rushil: Approximate "how far off the route am I?" as the distance from the user
    // to the closest polyline vertex in currentRoutePoints.
    private fun distanceFromUserToRoute(userLatLng: LatLng): Double {
        if (currentRoutePoints.size < 2) return Double.MAX_VALUE

        val results = FloatArray(1)
        var best = Double.MAX_VALUE

        for (pt in currentRoutePoints) {
            Location.distanceBetween(
                userLatLng.latitude,
                userLatLng.longitude,
                pt.latitude,
                pt.longitude,
                results
            )
            val d = results[0].toDouble()
            if (d < best) best = d
        }
        return best
    }





    // Rushil: Visually trim the route behind me so only the remaining part is drawn.
// I keep currentRoutePoints as the full path for ETA/turns; this only affects drawing.
    private fun updateRoutePolylineForProgress(location: Location) {
        if (navigationState != NavigationState.Active) return
        val googleMap = map ?: return
        val userLatLng = LatLng(location.latitude, location.longitude)

        // ========= CASE 1: Multi-leg route (CAR -> parking -> WALK) =========
        // While I'm still in the car leg, trim only the car polyline and leave the
        // walking leg (navigationPolylineWalk) alone. The walk leg will be used AFTER
        // I tap "I parked".
        if (activeMultiLegRoute != null && navigationPolylineWalk != null && navigationPolyline != null) {
            val carPoints = activeMultiLegRoute!!.carLegPoints
            if (carPoints.size < 2) return

            val results = FloatArray(1)
            var bestIndex = 0
            var bestDist = Double.MAX_VALUE

            // Find the nearest point along the CAR leg.
            for (i in carPoints.indices) {
                val pt = carPoints[i]
                Location.distanceBetween(
                    userLatLng.latitude,
                    userLatLng.longitude,
                    pt.latitude,
                    pt.longitude,
                    results
                )
                val d = results[0].toDouble()
                if (d < bestDist) {
                    bestDist = d
                    bestIndex = i
                }
            }

            // If I'm basically at / past the last car point, hide the car polyline.
            if (bestIndex >= carPoints.size - 1) {
                navigationPolyline?.remove()
                navigationPolyline = null
                return
            }

            val remaining = mutableListOf<LatLng>()
            remaining.add(userLatLng)
            for (i in bestIndex + 1 until carPoints.size) {
                remaining.add(carPoints[i])
            }

            navigationPolyline?.remove()
            navigationPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(remaining)
                    .width(12f)
                    .color(Color.BLUE)   // Rushil: car leg is solid blue
            )
            return
        }

        // ========= CASE 2: Single-leg walking route (WALK mode, or after "I parked") =========
        // Here navigationPolylineWalk is null / activeMultiLegRoute is null, and
        // navigationPolyline draws the entire remaining WALK route.
        if (currentRoutePoints.size < 2 || navigationPolyline == null) return

        val results = FloatArray(1)
        var bestIndex = 0
        var bestDist = Double.MAX_VALUE

        // Find nearest vertex on the full route.
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
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
            }
        }

        if (bestIndex >= currentRoutePoints.size - 1) {
            // At / past the end, nothing left to draw.
            navigationPolyline?.remove()
            navigationPolyline = null
            return
        }

        val remainingPoints = mutableListOf<LatLng>()
        remainingPoints.add(userLatLng)
        for (i in bestIndex + 1 until currentRoutePoints.size) {
            remainingPoints.add(currentRoutePoints[i])
        }

        // Dotted purple walking leg.
        val dashPattern: List<PatternItem> = listOf(
            Dot(),
            Gap(20f)
        )

        navigationPolyline?.remove()
        navigationPolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(remainingPoints)
                .width(12f)
                .color(Color.MAGENTA)      // Rushil: walking leg = purple
                .pattern(dashPattern)      // keep the dotted style even as it shrinks
        )
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
    // fullRoutePoints = entire path (user -> parking -> destination), used for ETA/remaining distance.
    // carLegPoints    = only the car portion, so I can style it differently.
    // walkLegPoints   = only the walking portion, so I can style it differently.
    private data class MultiLegRoute(
        val fullRoutePoints: List<LatLng>,
        val carLegPoints: List<LatLng>,
        val walkLegPoints: List<LatLng>,
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

        // Snap destination to nearest graph node (for walking leg).
        val destNode = findNearestNode(destLatLng) ?: return null

        // Rushil: For the CAR leg, try snapping to the nearest *car-capable* node.
        // If we can't find one, fall back to the generic nearest node.
        val carStartNode: NavNode? =
            findNearestCarCapableNode(userLatLng) ?: findNearestNode(userLatLng)

        if (carStartNode == null) return null

        // Rushil: For walking we still use normal snapping around the building, but
        // for the driving portion we start at carStartNode.


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
        val lotsToTry: List<ParkingLot> = forcedParkingLot?.let { listOf(it) } ?: parkingLots

        for (lot in lotsToTry) {
            // Snap parking lot location to nearest graph node.
            val parkingNode = findNearestNode(lot.latLng) ?: continue

            // Car leg: from the nearest *road node* to the lot (CAR edges only).
            val carPathNodeIds = graph.shortestPathNodeIds(
                startId = carStartNode.id,
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
                continue
            }

            // Rushil: Compute distances.
            val carPathDist = computePathLengthMeters(carPathNodeIds, nodeIndex)

            // Also include the short straight segment from the user's exact position
            // to the carStartNode in the CAR distance for better ETA.
            val userToCarStartDist = distanceMeters(
                userLatLng,
                LatLng(carStartNode.lat, carStartNode.lng)
            )

            val carDist = userToCarStartDist + carPathDist
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

        // Build separate polyline segments for car and walk legs.

        // Car leg: from the user's current location along the car path nodes to the parking lot.
        val carPoints = mutableListOf<LatLng>()
        carPoints.add(userLatLng)
        for (nodeId in best.carPathNodeIds) {
            val node = nodeIndex[nodeId] ?: continue
            carPoints.add(LatLng(node.lat, node.lng))
        }

        // Walk leg: from the parking lot toward the destination along the walk path nodes.
        val walkPoints = mutableListOf<LatLng>()

        // Start at the last car point (which should be near the parking node) to keep the line continuous.
        val lastCarPoint = carPoints.lastOrNull()
        if (lastCarPoint != null) {
            walkPoints.add(lastCarPoint)
        }

        for (nodeId in best.walkPathNodeIds) {
            val node = nodeIndex[nodeId] ?: continue
            walkPoints.add(LatLng(node.lat, node.lng))
        }

        // End exactly at the destination marker.
        walkPoints.add(destLatLng)

        // Full route = car leg + walk leg, but skip duplicate join point if it happens.
        val fullRoutePoints = mutableListOf<LatLng>()
        fullRoutePoints.addAll(carPoints)

        if (walkPoints.isNotEmpty()) {
            val firstWalk = walkPoints.first()
            val lastCar = carPoints.lastOrNull()
            val startIndex = if (lastCar != null &&
                lastCar.latitude == firstWalk.latitude &&
                lastCar.longitude == firstWalk.longitude
            ) {
                1 // skip the duplicate point
            } else {
                0
            }
            for (i in startIndex until walkPoints.size) {
                fullRoutePoints.add(walkPoints[i])
            }
        }

        return MultiLegRoute(
            fullRoutePoints = fullRoutePoints,
            carLegPoints = carPoints,
            walkLegPoints = walkPoints,
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

                when (navigationState) {
                    // Rushil: If I'm still in route PREVIEW (Start button showing),
                    // just rebuild the preview route with the new lot.
                    NavigationState.RoutePreview -> {
                        buildRoutePreviewFromCurrentLocation()
                    }

                    // Rushil: If I'm already actively navigating, rebuild and go
                    // straight back into live navigation.
                    NavigationState.Active -> {
                        startNavigationFromCurrentLocation()
                    }

                    else -> {
                        // Idle / Preview: nothing to do yet (no route computed).
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    // Rushil: User tapped "I parked". Drop the car leg and continue as a pure walking route.
    private fun handleIParkedTapped() {
        if (navigationState != NavigationState.Active) {
            Toast.makeText(requireContext(), "Start navigation first.", Toast.LENGTH_SHORT).show()
            return
        }

        val route = activeMultiLegRoute
        if (route == null) {
            Toast.makeText(requireContext(), "You're not on a car + walk route.", Toast.LENGTH_SHORT).show()
            return
        }

        val googleMap = map ?: return

        // 1) Switch nav mode to WALK (and sync the toggle UI).
        navModeToggleGroup.check(R.id.btnModeWalk)
        setNavMode(NavMode.WALK)

        // 2) Use only the walking leg for distance / ETA.
        setCurrentRoute(route.walkLegPoints)

        // 3) Remove car polyline; keep / redraw the walking leg.
        navigationPolyline?.remove()
        navigationPolyline = null

        if (navigationPolylineWalk == null) {
            // If for some reason the walk polyline wasn't drawn yet, draw it now.
            val dashPattern = listOf(Dot(), Gap(20f))
            navigationPolylineWalk = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(route.walkLegPoints)
                    .width(10f)
                    .color(Color.parseColor("#9C27B0")) // same purple you chose
                    .pattern(dashPattern)
            )
        }

        // 4) Refit camera around the walking leg so the user sees the walk segment.
        val builder = LatLngBounds.builder()
        route.walkLegPoints.forEach { builder.include(it) }
        val walkBounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(walkBounds, 150))

        // 5) Update UI text and hide "I parked" (no double-tapping).
        navParkingText.text = "Walking from ${route.parkingLot.name}"
        navIParkedButton.visibility = View.GONE

        Toast.makeText(
            requireContext(),
            "Switched to walking from ${route.parkingLot.name}.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Rushil: Cancel the current destination/route preview without starting live nav.
    private fun cancelNavigationPreview() {
        // Remove any preview route.
        navigationPolyline?.remove()
        navigationPolyline = null
        navigationPolylineWalk?.remove()
        navigationPolylineWalk = null

        // Clear route info so ETA falls back to default.
        currentRoutePoints = emptyList()
        currentRouteCumulativeMeters = emptyList()

        // Keep the marker visible (like Google Maps) but hide the panel + parking row.
        navigationState = NavigationState.Idle
        activeMultiLegRoute = null
        selectedParkingLot = null
        updateNavModeToggleEnabled()


        navPanelContainer.visibility = View.GONE
        navParkingContainer.visibility = View.GONE
        navIParkedButton.visibility = View.GONE
        updateFabPositionRelativeToNavPanel()
        setSearchUiVisible(true)
        navNextTurnContainer.visibility = View.GONE

    }

    // Rushil: Internal helper so existing code (like parking lot change while Active)
    // can rebuild the route and jump straight into Active nav.
    private fun startNavigationFromCurrentLocation() {
        buildRoutePreviewFromCurrentLocation()
        beginLiveNavigationFromPreview()
    }


    // Rushil: Only allow changing Walk/Drive mode before I've committed to a route.
// That means:
//
//   Idle           → no destination yet, toggle enabled.
//   Preview        → marker selected, "Navigate" showing, toggle enabled.
//   RoutePreview   → route drawn, "Start" showing, toggle DISABLED.
//   Active         → live nav, "Stop" showing, toggle DISABLED.
//
// Rushil: Only allow changing Walk/Drive mode before I've committed to a route.
// Also keep the toggle's visual state in sync with the actual currentNavMode.
    private fun updateNavModeToggleEnabled() {
        val enabled = navigationState == NavigationState.Idle ||
                navigationState == NavigationState.Preview

        if (!::navModeToggleGroup.isInitialized) return

        // Enable / disable the group.
        navModeToggleGroup.isEnabled = enabled

        if (enabled) {
            // Rushil: When the toggle becomes enabled again (Idle/Preview),
            // make sure the highlighted button matches my currentNavMode.
            val targetId = when (currentNavMode) {
                NavMode.WALK -> R.id.btnModeWalk
                NavMode.CAR  -> R.id.btnModeCar
            }

            if (navModeToggleGroup.checkedButtonId != targetId) {
                navModeToggleGroup.check(targetId)
            }
        }
    }

    // Rushil: Helper to show/hide the search UI as a block.
    private fun setSearchUiVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE

        // Hide/show the text field itself.
        searchInput?.visibility = v

        if (!visible) {
            // If nav is active, I never want suggestions popping up.
            suggestionsList?.visibility = View.GONE
        }
        // When visible == true, filterSuggestions() will control suggestionsList visibility.
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
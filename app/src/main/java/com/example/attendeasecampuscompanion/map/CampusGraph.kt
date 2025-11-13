package com.example.attendeasecampuscompanion.map

import org.json.JSONObject
import org.json.JSONArray

// Rushil: Enum so I can flag which travel modes are allowed on each edge.
// For now it's WALK and CAR. Campus footpaths will be WALK-only; roads may be WALK+CAR.
enum class TravelMode {
    WALK,
    CAR
}

// Rushil: Node = a point on the campus path graph (intersection, corner, building entrance).
data class NavNode(
    val id: Int,
    val lat: Double,
    val lng: Double
)

// Rushil: Edge = a walkable/drivable segment between two nodes.
// distanceMeters lets me compute ETA later; modes tells me if walking/driving is allowed.
data class NavEdge(
    val fromId: Int,
    val toId: Int,
    val distanceMeters: Double,
    val modes: Set<TravelMode>
)


// Rushil: CampusGraph = the full network of nodes + edges that I will use for routing.
data class CampusGraph(
    val nodes: List<NavNode>,
    val edges: List<NavEdge>
)

// Rushil: Helper object to load my campus graph from a JSON file in res/raw.
object CampusGraphLoader {

    // Rushil: Call this from a Fragment/Activity with a Context to load the graph from R.raw.campus_graph.
    fun loadFromRawJson(context: android.content.Context, rawResId: Int): CampusGraph {
        // Read the entire JSON file into a string.
        val jsonText = context.resources.openRawResource(rawResId)
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(jsonText)

        // ===================== NODES =====================
        val nodesJson = root.getJSONArray("nodes")
        val nodes = mutableListOf<NavNode>()

        for (i in 0 until nodesJson.length()) {
            val nodeObj = nodesJson.getJSONObject(i)
            val id = nodeObj.getInt("id")
            val lat = nodeObj.getDouble("lat")
            val lng = nodeObj.getDouble("lng")
            nodes.add(NavNode(id = id, lat = lat, lng = lng))
        }

        // ===================== EDGES =====================
        val edgesJson = root.getJSONArray("edges")
        val edges = mutableListOf<NavEdge>()

        for (i in 0 until edgesJson.length()) {
            val edgeObj = edgesJson.getJSONObject(i)
            val fromId = edgeObj.getInt("from")
            val toId = edgeObj.getInt("to")
            val distanceMeters = edgeObj.getDouble("distance_m")

            // Modes are stored as ["walk", "car"] in JSON; convert to enum set.
            val modesArray = edgeObj.optJSONArray("modes") ?: JSONArray()
            val modes = mutableSetOf<TravelMode>()
            for (j in 0 until modesArray.length()) {
                when (modesArray.getString(j).lowercase()) {
                    "walk" -> modes.add(TravelMode.WALK)
                    "car"  -> modes.add(TravelMode.CAR)
                }
            }
            // If no modes listed, default to WALK so it's at least usable.
            if (modes.isEmpty()) {
                modes.add(TravelMode.WALK)
            }

            edges.add(
                NavEdge(
                    fromId = fromId,
                    toId = toId,
                    distanceMeters = distanceMeters,
                    modes = modes
                )
            )
        }

        return CampusGraph(nodes = nodes, edges = edges)
    }
}
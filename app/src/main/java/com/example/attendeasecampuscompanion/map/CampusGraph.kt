package com.example.attendeasecampuscompanion.map

import org.json.JSONObject
import org.json.JSONArray
import java.util.PriorityQueue


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
// I also attach a shortestPathNodeIds() method (Dijkstra Algo.) so I can ask the graph for the best path.
data class CampusGraph(
    val nodes: List<NavNode>,
    val edges: List<NavEdge>
) {

    // Rushil: Internal helper for Dijkstra priority queue.
    private data class QueueEntry(
        val nodeId: Int,
        val distance: Double
    ) : Comparable<QueueEntry> {
        override fun compareTo(other: QueueEntry): Int =
            distance.compareTo(other.distance)
    }

    // Rushil: Dijkstra's algorithm to compute the shortest path between two node IDs,
    // using only edges that allow at least one of the allowed travel modes.
    // Returns a list of node IDs from start → end (inclusive), or emptyList() if no path.
    fun shortestPathNodeIds(
        startId: Int,
        endId: Int,
        allowedModes: Set<TravelMode>
    ): List<Int> {
        if (startId == endId) {
            return listOf(startId)
        }

        // Build an adjacency list filtered by allowed modes.
        // For campus paths, we treat edges as bidirectional.
        val adjacency: MutableMap<Int, MutableList<Pair<Int, Double>>> = mutableMapOf()

        fun addEdge(u: Int, v: Int, dist: Double) {
            val list = adjacency.getOrPut(u) { mutableListOf() }
            list.add(v to dist)
        }

        for (edge in edges) {
            // Only include edges that share at least one allowed mode.
            if (edge.modes.intersect(allowedModes).isNotEmpty()) {
                addEdge(edge.fromId, edge.toId, edge.distanceMeters)
                addEdge(edge.toId, edge.fromId, edge.distanceMeters) // bidirectional
            }
        }

        // If no adjacency for start or end, give up early.
        if (!adjacency.containsKey(startId) || !adjacency.containsKey(endId)) {
            return emptyList()
        }

        val dist: MutableMap<Int, Double> = mutableMapOf()
        val prev: MutableMap<Int, Int?> = mutableMapOf()
        val visited: MutableSet<Int> = mutableSetOf()

        val queue = PriorityQueue<QueueEntry>()
        dist[startId] = 0.0
        queue.add(QueueEntry(startId, 0.0))

        while (queue.isNotEmpty()) {
            val (u, d) = queue.poll()

            if (u in visited) continue
            visited.add(u)

            if (u == endId) {
                break
            }

            val neighbors = adjacency[u] ?: continue
            for ((v, weight) in neighbors) {
                if (v in visited) continue
                val alt = d + weight
                val current = dist[v]
                if (current == null || alt < current) {
                    dist[v] = alt
                    prev[v] = u
                    queue.add(QueueEntry(v, alt))
                }
            }
        }

        // Reconstruct path from endId backward.
        if (!dist.containsKey(endId)) {
            // No path found.
            return emptyList()
        }

        val path = mutableListOf<Int>()
        var current: Int? = endId
        while (current != null) {
            path.add(current)
            current = prev[current]
        }
        path.reverse() // Now startId → endId

        return path
    }
}


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
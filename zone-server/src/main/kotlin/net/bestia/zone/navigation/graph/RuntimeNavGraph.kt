package net.bestia.zone.navigation.graph

import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.zone.geometry.Vec3L

/**
 * The world's navigation graph in the coordinates and layout the tick loop wants.
 *
 * Worldgen hands over a [NavGraph] in world-space **metres** as `Vec2d`; everything in `zone-server` works
 * in **voxel** units as [Vec3L]. Converting per query would put a divide and a ground lookup on the
 * pathfinder's inner loop, so it happens exactly once, here, at world load.
 *
 * The vertical is deliberately **not** filled in: it stays zero. The macro graph is a two-dimensional thing -
 * a route is a sequence of places, and how high each place is is a property of the ground under it, which the
 * local tier reads from the walkability tiles at the moment it turns a leg into steps. See
 * [NavWorldSource.place] for why obtaining it here is both pointless and expensive.
 *
 * Immutable. What changes at runtime is which edges are currently *usable*, and that is
 * [MacroGraphService]'s overlay rather than a mutation of this.
 */
class RuntimeNavGraph(
  /** Node positions, indexed by the node's own id. */
  val nodePositions: Array<Vec3L>,
  val nodeKinds: Array<NavNodeKindView>,
  val edges: List<RuntimeNavEdge>,
  /** Edge indices touching each node, both directions, indexed by node id. */
  val incidentEdges: Array<IntArray>
) {

  val nodeCount get() = nodePositions.size

  val isEmpty get() = nodePositions.isEmpty()

  /**
   * The node nearest [position], or null when the graph is empty.
   *
   * A linear scan, deliberately. A world holds a few thousand nodes, this is asked once per journey rather
   * than once per step, and a spatial index would be a second structure to keep correct for a microsecond
   * that nothing is waiting on. If long-range travel ever becomes something hundreds of NPCs re-plan every
   * tick, this is the line to revisit - not before.
   */
  fun nearestNode(position: Vec3L, maxDistance: Double = Double.MAX_VALUE): Int? {
    var best = -1
    var bestSq = maxDistance * maxDistance

    for (id in nodePositions.indices) {
      val other = nodePositions[id]
      val dx = (other.x - position.x).toDouble()
      val dy = (other.y - position.y).toDouble()
      val distanceSq = dx * dx + dy * dy
      if (distanceSq <= bestSq) {
        bestSq = distanceSq
        best = id
      }
    }

    return if (best < 0) null else best
  }

  /** Mirrors `worldgen`'s `NavNodeKind` without exposing the generator's type to the game code. */
  enum class NavNodeKindView { SETTLEMENT, GATE, BRIDGE_APPROACH, CAVE_ENTRANCE, WILDERNESS }

  companion object {

    /**
     * Adapts a generated graph for the runtime.
     *
     * @param place converts a world-space position in **metres** into ECS position units, ground included.
     *   Supplied by the caller rather than computed here, because the metres-to-position-unit convention is
     *   `world.stream.ChunkCoords`'s to state - see its class note, which is explicit that the repository has
     *   not always been unanimous about it. One conversion, one owner.
     */
    fun of(
      source: NavGraph,
      place: (Double, Double) -> Vec3L
    ): RuntimeNavGraph {
      val positions = Array(source.nodes.size) { id ->
        val node = source.nodes[id]
        place(node.position.x, node.position.y)
      }

      val kinds = Array(source.nodes.size) {
        NavNodeKindView.valueOf(source.nodes[it].kind.name)
      }

      val edges = source.edges.map { edge ->
        RuntimeNavEdge(
          a = edge.a.value,
          b = edge.b.value,
          kind = edge.kind,
          modes = edge.modes,
          baseCost = edge.baseCost,
          lengthMetres = edge.lengthMetres,
          maxAgentHalfWidth = edge.maxAgentHalfWidth,
          waypoints = edge.waypoints?.map { place(it.x, it.y) } ?: emptyList()
        )
      }

      val incident = Array(source.nodes.size) { IntArray(0) }
      val buckets = Array(source.nodes.size) { ArrayList<Int>(4) }
      for ((index, edge) in edges.withIndex()) {
        buckets[edge.a].add(index)
        buckets[edge.b].add(index)
      }
      for (id in incident.indices) incident[id] = buckets[id].toIntArray()

      return RuntimeNavGraph(positions, kinds, edges, incident)
    }

    val EMPTY = RuntimeNavGraph(emptyArray(), emptyArray(), emptyList(), emptyArray())
  }
}

/**
 * One macro connection, ready to walk.
 *
 * Endpoints are plain `Int` node ids rather than a value class: they index [RuntimeNavGraph.nodePositions]
 * and [RuntimeNavGraph.incidentEdges] directly, thousands of times per search.
 */
class RuntimeNavEdge(
  val a: Int,
  val b: Int,
  val kind: NavEdgeKind,
  /** Capabilities the edge demands - **all** of them, see `worldgen`'s `MovementMode`. */
  val modes: Set<MovementMode>,
  val baseCost: Double,
  val lengthMetres: Double,
  val maxAgentHalfWidth: Double,
  /** Intermediate points, already in voxel units. Empty when the edge is a straight line. */
  val waypoints: List<Vec3L>
) {

  fun other(end: Int): Int = if (end == a) b else a

  /** True when this edge is a graded surface, and so the fast way for anything that prefers one. */
  val isMadeSurface: Boolean
    get() = kind == NavEdgeKind.ROAD || kind == NavEdgeKind.BRIDGE || kind == NavEdgeKind.GATE_SPOKE

  override fun toString() = "$kind[$a<->$b ${lengthMetres.toInt()}m $modes]"
}

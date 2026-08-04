package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Vec2d
import kotlin.math.floor

/**
 * Index of a [NavNode] within its graph's own node list.
 *
 * Dense and 0-based, so `graph.nodes[id.value].id == id` - a deliberate constraint the constructor
 * checks. A runtime pathfinder addresses nodes several thousand times per search and an `IntArray`
 * indexed by this is the difference between that and as many hash lookups.
 */
@JvmInline
value class NavNodeId(val value: Int) {
  override fun toString() = "N$value"
}

/** What put a node on the graph. Diagnostic, and the thing a runtime ranks travel destinations by. */
enum class NavNodeKind { SETTLEMENT, GATE, BRIDGE_APPROACH, CAVE_ENTRANCE, WILDERNESS }

/**
 * A capability an edge **demands** of anything crossing it.
 *
 * The set is a conjunction: a traveller needs *every* mode on an edge, not one of them. That is the whole
 * mechanism behind "cannot swim", and getting it backwards is subtle enough to be worth stating - an
 * open-country hop that fords a stream carries `{WALK, SWIM}`, and under an any-of reading a creature that
 * cannot swim would satisfy it on `WALK` alone and then walk into the river.
 *
 * Deliberately only capabilities. There is no `ROAD` mode, because being on a road demands nothing of
 * anybody - what a road offers is *speed*, which is a preference rather than a gate, and preference is
 * [NavEdgeKind]'s job. Having it in both places meant every road edge asserted a capability every creature
 * trivially had, which reads like a restriction and restricts nothing.
 */
enum class MovementMode {
  /** Ordinary ground. Anything that walks at all has this. */
  WALK,

  /** Open or flowing water: a ford, a crossing with no deck, a shipping lane. */
  SWIM,

  /** Steeper than comfortable, but under the walkability cutoff. See `NavParams.walkableSlopeDegrees`. */
  CLIMB
}

/**
 * What the edge is, physically.
 *
 * Carried separately from [MovementMode] because it is what a per-species cost multiplier keys off: a
 * wild bestia avoids a `ROAD` because roads are where people are, not because of how a road is walked.
 * One graph therefore serves every species - the tags say what is possible, the kind says what is
 * *preferred*, and the consumer decides the second without the generator having to guess.
 */
enum class NavEdgeKind {
  ROAD,
  BRIDGE,
  SEA_LANE,

  /**
   * A settlement centre to one of its own gates. The short hop that joins a town to its approaches.
   *
   * There is deliberately no `STREET` kind beside this. A town holds hundreds of street segments, all of
   * them inside a few hundred metres of the settlement node, and none of them is a decision a *journey*
   * makes - "which way through town" is local movement over real ground, which is the chunk tier's job.
   * Modelling them here bought a graph three times the size in which every street collapsed onto the
   * settlement centre anyway.
   */
  GATE_SPOKE,

  /** Open country, routed over the movement-cost field rather than following any built thing. */
  WILDERNESS
}

/**
 * One end of a traversable connection.
 *
 * Positions are world-space metres, the same space every [net.bestia.worldgen.vector.VectorFeature] lives
 * in - not cell indices, which would pin the graph to whatever resolution the producing stage happened to
 * run at.
 */
class NavNode(
  val id: NavNodeId,
  val position: Vec2d,
  val kind: NavNodeKind,
  /**
   * [net.bestia.worldgen.civ.SettlementChannels.INDEX] this node belongs to, or -1 when it belongs to no
   * settlement. Set on [NavNodeKind.SETTLEMENT] and [NavNodeKind.GATE] nodes.
   */
  val settlementIndex: Int = -1,
  /**
   * False once history abandoned the settlement this node belongs to. Always true for wilderness nodes.
   *
   * Kept as a flag rather than dropping the node, because a ruin is still a place an NPC can be sent to
   * and still a junction the roads to it converge on.
   */
  val standing: Boolean = true,
  /** [LayerId.CIVILISATION_DISTANCE] here, in metres. For a consumer weighting danger without resampling. */
  val civilisationDistance: Double = 0.0
) {
  override fun toString() = "$kind[$id at $position]"
}

/**
 * A traversable connection between two nodes, usable in both directions.
 *
 * Undirected because everything it is built from is: a road is a road both ways, and
 * [net.bestia.worldgen.civ.RouteFinder] averages the two cells' cost per step precisely so that a route
 * costs the same in either direction.
 */
class NavEdge(
  val a: NavNodeId,
  val b: NavNodeId,
  val kind: NavEdgeKind,
  val lengthMetres: Double,
  /**
   * Traversal cost on the same scale [LayerId.MOVEMENT_COST] uses: one per metre over flat open ground.
   *
   * A *baseline*, not a final answer. The consumer scales it per species from [kind] and [modes] - a
   * merchant discounts `ROAD`, a wild animal marks it up - which is why there is one graph rather than one
   * per creature type.
   */
  val baseCost: Double,
  val modes: Set<MovementMode>,
  /**
   * Half the width of the narrowest point on the edge, in metres, or [Double.MAX_VALUE] when nothing
   * constrains it. A bridge's carriageway; the reason "too big for this bridge" needs no other data.
   */
  val maxAgentHalfWidth: Double = Double.MAX_VALUE,
  /**
   * Intermediate points between the two endpoints, or null when the edge is a straight line.
   *
   * Load-bearing rather than cosmetic: a road edge can span kilometres of bends, and a consumer refining
   * it into precise steps walks *between consecutive waypoints*. Given only two endpoints it would walk
   * the chord and leave the road.
   */
  val waypoints: List<Vec2d>? = null
) {

  init {
    require(modes.isNotEmpty()) { "A $kind edge with no movement mode is untraversable; drop it instead" }
    require(lengthMetres >= 0.0) { "lengthMetres must not be negative, was $lengthMetres" }
    require(baseCost >= 0.0) { "baseCost must not be negative, was $baseCost" }
  }

  fun other(end: NavNodeId): NavNodeId = when (end) {
    a -> b
    b -> a
    else -> throw IllegalArgumentException("$end is not an end of this edge")
  }

  override fun toString() = "$kind[$a<->$b ${lengthMetres.toInt()}m $modes]"
}

/**
 * The macro navigation graph: where an NPC can get to, and how expensive each way is.
 *
 * ### What this is and is not
 *
 * Coarse on purpose. Nodes are the places a journey has a reason to pass through - a town, a gate, either
 * end of a bridge, a cave mouth - plus a sparse lattice over open country so that something avoiding
 * roads still has a way across the map. A few thousand nodes for a world, held entirely in memory.
 *
 * It is **not** a navmesh and does not know about individual voxels. Turning one macro edge into precise
 * steps over the actual ground is the chunk tier's job, through
 * [net.bestia.worldgen.derived.WalkableTile] - which is deliberately the opposite trade: exact, local, and
 * rebuilt when the ground changes. The two tiers meet at an edge's [NavEdge.waypoints].
 *
 * ### Immutability
 *
 * Generated once with the rest of the world tier and read-only forever, like every other stage output.
 * Nothing here models a bridge being destroyed *during play*: the runtime keeps its own sparse overlay of
 * what is currently blocked, keyed on the indices below, and those indices are stable for the life of a
 * generated world precisely because this never changes.
 */
class NavGraph(
  val nodes: List<NavNode>,
  val edges: List<NavEdge>
) {

  init {
    for ((index, node) in nodes.withIndex()) {
      require(node.id.value == index) {
        "Node ids must be dense and match their position: nodes[$index] has ${node.id}"
      }
    }
    for (edge in edges) {
      require(edge.a.value in nodes.indices) { "Edge endpoint ${edge.a} is not a node in this graph" }
      require(edge.b.value in nodes.indices) { "Edge endpoint ${edge.b} is not a node in this graph" }
      require(edge.a != edge.b) { "Self-loop at ${edge.a} - an edge from a node to itself is not a route" }
    }
  }

  /**
   * Indices into [edges] touching each node, both directions, indexed by [NavNodeId.value].
   *
   * Lazy because the offline tools that only count or draw the graph never expand a node, and building
   * this for them would be the largest allocation in a stage that otherwise holds a few thousand objects.
   */
  val incidentEdges: List<IntArray> by lazy {
    val buckets = Array(nodes.size) { ArrayList<Int>(4) }
    for ((index, edge) in edges.withIndex()) {
      buckets[edge.a.value].add(index)
      buckets[edge.b.value].add(index)
    }
    buckets.map { it.toIntArray() }
  }

  /**
   * The node closest to [point], or null when none is within [maxDistance].
   *
   * How an actor standing in a field enters the graph. Bucketed at [maxDistance] rather than scanned,
   * because "which node do I start from" is asked once per journey per NPC and a linear scan over a
   * whole world's nodes is a surprising amount of work to hide behind an innocent-looking call.
   *
   * Ties broken by node id so the answer never depends on iteration order.
   */
  fun nearestNode(point: Vec2d, maxDistance: Double = Double.MAX_VALUE): NavNode? {
    if (nodes.isEmpty()) return null

    val index = bucketIndex
    val limitSq = maxDistance * maxDistance

    // A nine-bucket look-up only covers a radius up to one bucket edge. Beyond that - and for an unbounded
    // query, which has no radius at all - scan. Correctness first: silently missing a node that is
    // ten buckets away but genuinely nearest is the kind of bug that shows up as one NPC standing still.
    if (maxDistance > index.bucketMetres) {
      var best: NavNode? = null
      var bestSq = Double.MAX_VALUE
      for (candidate in nodes) {
        val distanceSq = candidate.position.distanceSquaredTo(point)
        if (distanceSq > limitSq) continue
        if (distanceSq < bestSq) {
          best = candidate
          bestSq = distanceSq
        }
      }
      return best
    }
    val bx = floor(point.x / index.bucketMetres).toLong()
    val by = floor(point.y / index.bucketMetres).toLong()

    var best: NavNode? = null
    var bestSq = Double.MAX_VALUE

    for (dy in -1..1) {
      for (dx in -1..1) {
        val bucket = index.buckets[BucketIndex.key(bx + dx, by + dy)] ?: continue
        for (candidate in bucket) {
          val distanceSq = candidate.position.distanceSquaredTo(point)
          if (distanceSq > limitSq) continue
          if (distanceSq < bestSq || (distanceSq == bestSq && candidate.id.value < (best?.id?.value ?: Int.MAX_VALUE))) {
            best = candidate
            bestSq = distanceSq
          }
        }
      }
    }

    return best
  }

  private val bucketIndex: BucketIndex by lazy { BucketIndex.of(nodes) }

  /**
   * A bucket grid over the nodes, sized so that a nine-bucket look-up covers any radius up to one bucket.
   *
   * The same shape `SettlementStage`'s own separation index uses, for the same reason it does.
   */
  private class BucketIndex(val bucketMetres: Double, val buckets: Map<Long, List<NavNode>>) {

    companion object {
      fun of(nodes: List<NavNode>): BucketIndex {
        val bucketMetres = DEFAULT_BUCKET_METRES
        val buckets = HashMap<Long, ArrayList<NavNode>>()
        for (node in nodes) {
          val bx = floor(node.position.x / bucketMetres).toLong()
          val by = floor(node.position.y / bucketMetres).toLong()
          buckets.getOrPut(key(bx, by)) { ArrayList() }.add(node)
        }
        return BucketIndex(bucketMetres, buckets)
      }

      fun key(bx: Long, by: Long): Long = (bx shl 32) or (by and 0xFFFFFFFFL)

      /**
       * Bucket edge, in metres.
       *
       * A nine-bucket look-up is only correct for a search radius up to one bucket, so this has to be at
       * least as large as the widest `maxDistance` a caller passes - in practice a wilderness node
       * spacing. Ten kilometres covers the default spacing with room to spare, and a bucket that holds a
       * handful of nodes is the point of the grid rather than a cost.
       */
      private const val DEFAULT_BUCKET_METRES = 10_000.0
    }
  }

  override fun toString() = "NavGraph[${nodes.size} nodes, ${edges.size} edges]"

  companion object {
    /** What a world whose pipeline had no navigation stage carries. See `World.navGraph`. */
    val EMPTY = NavGraph(emptyList(), emptyList())
  }
}

package net.bestia.zone.travel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdge
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.NavNode
import net.bestia.worldgen.core.NavNodeId
import net.bestia.worldgen.core.NavNodeKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.graph.NavWorldSource
import net.bestia.zone.navigation.local.LocalWalkQuery

/**
 * Flat country with a river: a road crosses it by a bridge, and a long way round goes by a ford upstream.
 *
 * ```
 *   WEST ──road── BRIDGE_WEST ══bridge══ BRIDGE_EAST ──road── EAST
 *     └────────────── FORD (far north) ──────────────────────────┘
 * ```
 *
 * One position unit is one metre and a chunk is 32. The bridgeheads stand on the banks. [breakBridge] takes
 * the deck away the way an edit would: the ground under it stops being standable and its chunk is reported
 * changed.
 */
class BridgeCountry {

  var bridgeStanding = true
    private set

  private val chunkChanged = ArrayList<(ChunkPos) -> Unit>()

  val walkQuery = object : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = isGround(to)
    override fun surfaceAt(position: Vec3L) = if (isGround(position)) 0L else null
    override fun isResident(position: Vec3L) = true
  }

  private val source = object : NavWorldSource {
    override val isReady get() = true
    override fun navGraph() = graph()
    override fun place(metresX: Double, metresY: Double) = Vec3L(metresX.toLong(), metresY.toLong(), 0L)
    override fun chunkAt(position: Vec3L) = chunkOf(position)
    override fun onChunkChanged(handler: (ChunkPos) -> Unit) {
      chunkChanged.add(handler)
    }
  }

  val macroGraph = MacroGraphService(source, walkQuery, NavigationConfig()).also { it.ensureLoaded() }

  /** The bridge's edge in the macro graph. */
  val bridgeEdge = 1

  fun breakBridge() {
    bridgeStanding = false
    announceBridgeEdit()
  }

  fun repairBridge() {
    bridgeStanding = true
    announceBridgeEdit()
  }

  private fun announceBridgeEdit() {
    chunkChanged.forEach { it(chunkOf(DECK[1])) }
    macroGraph.revalidate(budget = Int.MAX_VALUE)
  }

  private fun isGround(position: Vec3L): Boolean {
    val onRiver = position.x in RIVER_WEST..RIVER_EAST && position.y < RIVER_SOURCE
    return !onRiver || (bridgeStanding && position.y in -2L..2L)
  }

  private fun graph(): NavGraph {
    val nodes = listOf(WEST, BRIDGE_WEST, BRIDGE_EAST, EAST, FORD).mapIndexed { i, at ->
      NavNode(NavNodeId(i), Vec2d(at.x.toDouble(), at.y.toDouble()), NavNodeKind.WILDERNESS)
    }
    val edges = listOf(
      edge(0, 1, NavEdgeKind.ROAD),
      edge(1, 2, NavEdgeKind.BRIDGE, waypoints = DECK),
      edge(2, 3, NavEdgeKind.ROAD),
      edge(0, 4, NavEdgeKind.WILDERNESS),
      edge(4, 3, NavEdgeKind.WILDERNESS),
    )
    return NavGraph(nodes, edges)
  }

  private fun edge(a: Int, b: Int, kind: NavEdgeKind, waypoints: List<Vec3L>? = null): NavEdge {
    val from = POINTS[a]
    val to = POINTS[b]
    val length = Math.hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
    val points = waypoints?.map { Vec2d(it.x.toDouble(), it.y.toDouble()) }
    return NavEdge(NavNodeId(a), NavNodeId(b), kind, length, length, setOf(MovementMode.WALK), waypoints = points)
  }

  companion object {
    val WEST = Vec3L(0, 0, 0)
    val BRIDGE_WEST = Vec3L(140, 0, 0)
    val BRIDGE_EAST = Vec3L(180, 0, 0)
    val EAST = Vec3L(320, 0, 0)
    val FORD = Vec3L(160, 600, 0)

    private val POINTS = listOf(WEST, BRIDGE_WEST, BRIDGE_EAST, EAST, FORD)

    /** Where the deck spans the river, which is what an edit takes away. */
    private val DECK = listOf(Vec3L(150, 0, 0), Vec3L(160, 0, 0), Vec3L(170, 0, 0))

    private const val RIVER_WEST = 145L
    private const val RIVER_EAST = 175L

    /** The river rises well south of the ford, so the way round is dry all along. */
    private const val RIVER_SOURCE = 400L

    fun chunkOf(position: Vec3L): ChunkPos {
      return ChunkPos(Math.floorDiv(position.x, 32L).toInt(), Math.floorDiv(position.y, 32L).toInt(), 0)
    }
  }
}

package net.bestia.zone.navigation

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
import net.bestia.zone.navigation.graph.NavWorldSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Which nodes a course keeps at its far end.
 *
 * ```
 *   TOWN ────────────── road ────────────── JUNCTION
 *    (0,0)                                  (1000,0)
 * ```
 *
 * A traveller just past halfway sets off from the junction's side; where it clicked decides whether the town
 * is on its way or behind it.
 */
class PlanCourseTest {

  @Test
  fun `a destination nearest a town on the near side of it is walked to straight`() {
    // The town is the nearest node to the click, but the traveller is nearer still: the old course walked
    // into the town centre and back out again.
    val course = plan(Road(), from = Vec3L(520, 0, 0), destination = Vec3L(300, -350, 0))

    assertEquals(emptyList(), course.nodes)
  }

  @Test
  fun `a destination past the town goes by the town`() {
    val course = plan(Road(), from = Vec3L(520, 0, 0), destination = Vec3L(-100, 0, 0))

    assertEquals(listOf(Road.TOWN), course.nodes)
  }

  @Test
  fun `a course over a bridge is kept even when the far bank is nearer as the crow flies`() {
    // The town's end of the bridge is the traveller's nearest node and the far end the click's, yet the
    // traveller is nearer the click than the far bridgehead is: the straight line would cross the river.
    val course = plan(Road(bridge = true), from = Vec3L(450, 300, 0), destination = Vec3L(600, 900, 0))

    assertEquals(listOf(Road.JUNCTION), course.nodes)
  }

  private fun plan(road: Road, from: Vec3L, destination: Vec3L): MacroCourse {
    val navigation = TestNavigation.service(worldSource = road.source)
    return assertNotNull(navigation.planCourse(from, destination, profileId = null) { false })
  }

  /** The road in the diagram, or with [bridge] a bridge in its place, the town on one bank. */
  private class Road(private val bridge: Boolean = false) {

    val source = object : NavWorldSource {
      override val isReady get() = true
      override fun navGraph() = graph()
      override fun place(metresX: Double, metresY: Double) = Vec3L(metresX.toLong(), metresY.toLong(), 0L)
      override fun chunkAt(position: Vec3L): ChunkPos? = null
      override fun onChunkChanged(handler: (ChunkPos) -> Unit) = Unit
    }

    private fun graph(): NavGraph {
      val points = listOf(TOWN_AT, JUNCTION_AT)
      val nodes = points.mapIndexed { i, at ->
        NavNode(NavNodeId(i), Vec2d(at.x.toDouble(), at.y.toDouble()), NavNodeKind.WILDERNESS)
      }
      val length = Math.hypot((points[1].x - points[0].x).toDouble(), (points[1].y - points[0].y).toDouble())
      val kind = if (bridge) NavEdgeKind.BRIDGE else NavEdgeKind.ROAD
      val edge = NavEdge(NavNodeId(0), NavNodeId(1), kind, length, length, setOf(MovementMode.WALK))

      return NavGraph(nodes, listOf(edge))
    }

    companion object {
      const val TOWN = 0
      const val JUNCTION = 1

      private val TOWN_AT = Vec3L(0, 0, 0)
      private val JUNCTION_AT = Vec3L(1000, 0, 0)
    }
  }
}

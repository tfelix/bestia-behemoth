package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdge
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.NavNode
import net.bestia.worldgen.core.NavNodeId
import net.bestia.worldgen.core.NavNodeKind
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.Vec2d
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The overlay's cost controls, asserted as behaviour rather than as milliseconds.
 *
 * Deliberately no timing assertions - `ChunkService`'s own note on its slab-computation counter makes the case:
 * a test that watches a deterministic counter is a test, and one that watches a stopwatch is a flake. What is
 * pinned here is the *work* the overlay decides to do, which is what the wall time was a proxy for.
 *
 * For the record, the measurements these guard: on a 512 km lattice at whole-world zoom the first version took
 * 255 ms a frame, which is unpannable. Making the lattice colours opaque, reusing colour instances, and
 * following an edge's waypoints only when they are more than a few pixels apart brought that to 6 ms, and a
 * 4096 km world with a hundred and thirty thousand edges to 6.5 ms.
 */
class NavGraphOverlayTest {

  /** A square lattice, the shape the generator's wilderness pass makes. */
  private fun lattice(across: Int, spacing: Double, waypointsPerEdge: Int = 4): NavGraph {
    val nodes = ArrayList<NavNode>()
    for (y in 0 until across) {
      for (x in 0 until across) {
        nodes.add(
          NavNode(
            NavNodeId(nodes.size),
            Vec2d(x * spacing, y * spacing),
            // A scattering of hubs, so the node-level tests have both kinds to look at.
            if (x == 0 && y == 0) NavNodeKind.SETTLEMENT else NavNodeKind.WILDERNESS
          )
        )
      }
    }

    val edges = ArrayList<NavEdge>()
    fun connect(a: Int, b: Int, kind: NavEdgeKind) {
      val pa = nodes[a].position
      val pb = nodes[b].position
      val waypoints = (1..waypointsPerEdge).map {
        val t = it.toDouble() / (waypointsPerEdge + 1)
        Vec2d(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t)
      }
      edges.add(
        NavEdge(
          NavNodeId(a), NavNodeId(b), kind,
          lengthMetres = spacing, baseCost = spacing,
          modes = setOf(MovementMode.WALK), waypoints = waypoints
        )
      )
    }

    for (y in 0 until across) {
      for (x in 0 until across) {
        val i = y * across + x
        // One row and one column of road, so structural and wilderness both exist in useful numbers.
        if (x + 1 < across) connect(i, i + 1, if (y == 0) NavEdgeKind.ROAD else NavEdgeKind.WILDERNESS)
        if (y + 1 < across) connect(i, i + across, NavEdgeKind.WILDERNESS)
      }
    }

    return NavGraph(nodes, edges)
  }

  private fun draw(
    graph: NavGraph,
    metresPerPixel: Double,
    centre: Double,
    showWilderness: Boolean = true,
    canvas: Int = 400
  ): NavGraphOverlay.Stats {
    val image = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
      return NavGraphOverlay(graph).draw(
        g,
        Viewport(centre, centre, metresPerPixel, canvas, canvas),
        showWilderness
      )
    } finally {
      g.dispose()
    }
  }

  @Test
  fun `an empty graph draws nothing at all`() {
    val stats = draw(NavGraph.EMPTY, metresPerPixel = 100.0, centre = 0.0)

    assertEquals(0, stats.edgesInView)
    assertEquals(0, stats.edgesDrawn)
    assertFalse(stats.capped)
  }

  @Test
  fun `culling keeps a zoomed-in view from touching the whole graph`() {
    // The property that makes every zoom short of the whole world cheap regardless of world size.
    val graph = lattice(across = 40, spacing = 8_000.0)

    val whole = draw(graph, metresPerPixel = 8_000.0 * 40 / 400, centre = 8_000.0 * 20)
    val corner = draw(graph, metresPerPixel = 40.0, centre = 0.0)

    assertTrue(whole.edgesInView > 1000, "the whole-world view should see most of the graph")
    assertTrue(
      corner.edgesInView < whole.edgesInView / 50,
      "a 16 km view saw ${corner.edgesInView} of ${whole.edgesInView} edges - the cull is not working"
    )
  }

  @Test
  fun `the cap bounds the work but still reports the real total`() {
    // The pathological case: a world whose whole graph is in view at once. Drawing has to stop, and the status
    // line has to be able to say how much was left out - a silently partial overlay is worse than a capped one.
    val across = 260
    val spacing = 16_000.0
    val graph = lattice(across = across, spacing = spacing, waypointsPerEdge = 2)
    assertTrue(
      graph.edges.size > NavGraphOverlay.MAX_EDGES_PER_FRAME,
      "this test needs a graph larger than the cap, has ${graph.edges.size}"
    )

    // The scale is chosen so every edge is a few pixels long *and* the whole world fits. Zoom out further and
    // the cap stops being reached at all, because each edge falls under the sub-pixel skip and is dropped
    // before it costs anything - cheaper still, but not what this test is about.
    val stats = draw(
      graph,
      metresPerPixel = spacing / 2.5,
      centre = spacing * across / 2,
      canvas = 800
    )

    assertTrue(stats.capped, "a graph of ${graph.edges.size} edges in one view should have capped")
    assertEquals(NavGraphOverlay.MAX_EDGES_PER_FRAME, stats.edgesDrawn)
    assertTrue(
      stats.edgesInView > stats.edgesDrawn,
      "the total in view (${stats.edgesInView}) should exceed what was drawn (${stats.edgesDrawn})"
    )
  }

  @Test
  fun `a capped frame spends its budget on infrastructure before open country`() {
    // What makes the cap defensible: at a zoom where not everything fits, the roads are the part carrying
    // information. If the order were arbitrary a capped view would show a random patch of lattice instead.
    val across = 260
    val graph = lattice(across = across, spacing = 16_000.0, waypointsPerEdge = 2)
    val roads = graph.edges.count { it.kind == NavEdgeKind.ROAD }

    val onlyRoads = draw(
      graph,
      metresPerPixel = 16_000.0 / 2.5,
      centre = 16_000.0 * across / 2,
      showWilderness = false,
      canvas = 800
    )

    // Every road fits well inside the cap, so switching the lattice off has to show all of them.
    assertFalse(onlyRoads.capped)
    assertTrue(
      onlyRoads.edgesInView >= roads - 2,
      "expected about $roads roads in view, saw ${onlyRoads.edgesInView}"
    )
  }

  @Test
  fun `hiding the wilderness leaves far less to draw`() {
    val graph = lattice(across = 40, spacing = 8_000.0)
    val scale = 8_000.0 * 40 / 400
    val centre = 8_000.0 * 20

    val all = draw(graph, scale, centre, showWilderness = true)
    val structural = draw(graph, scale, centre, showWilderness = false)

    assertTrue(
      structural.edgesInView < all.edgesInView / 10,
      "roads alone (${structural.edgesInView}) should be a small fraction of everything (${all.edgesInView})"
    )
  }

  @Test
  fun `the lattice nodes disappear at coarse zoom and reappear when zoomed in`() {
    // Tens of thousands of dots closer together than a few pixels are a texture over the roads rather than
    // information, so they are dropped - but only while they would be unreadable.
    val graph = lattice(across = 40, spacing = 8_000.0)

    val coarse = draw(graph, metresPerPixel = 8_000.0 * 40 / 400, centre = 8_000.0 * 20)
    val close = draw(graph, metresPerPixel = 100.0, centre = 8_000.0 * 20)

    assertTrue(coarse.nodesDrawn < 50, "coarse zoom drew ${coarse.nodesDrawn} nodes; the lattice should be hidden")
    assertTrue(close.nodesDrawn > 0, "zoomed in, the lattice nodes should be visible")
  }

  @Test
  fun `edge colours are shared instances`() {
    // Not cosmetic: the draw loop skips `setColor` when the wanted colour is identical *by reference* to the one
    // already set, which silently stops working if these are rebuilt per call. That was worth several
    // milliseconds a frame, and nothing about the picture would reveal its loss.
    val walk = setOf(MovementMode.WALK)

    assertSame(
      NavGraphOverlay.colorOf(NavEdgeKind.WILDERNESS, walk),
      NavGraphOverlay.colorOf(NavEdgeKind.WILDERNESS, walk)
    )
    assertSame(
      NavGraphOverlay.colorOf(NavEdgeKind.ROAD, walk),
      NavGraphOverlay.colorOf(NavEdgeKind.ROAD, walk)
    )
    assertSame(
      NavGraphOverlay.colorOf(NavNodeKind.SETTLEMENT),
      NavGraphOverlay.colorOf(NavNodeKind.SETTLEMENT)
    )
  }

  @Test
  fun `lattice colours are opaque, because translucency is what made it slow`() {
    // The single largest win of the lot: a colour with alpha puts Java2D on its per-pixel blending path, and at
    // whole-world zoom the lattice is most of the ink on the map.
    for (modes in listOf(
      setOf(MovementMode.WALK),
      setOf(MovementMode.WALK, MovementMode.SWIM),
      setOf(MovementMode.WALK, MovementMode.CLIMB)
    )) {
      val color = NavGraphOverlay.colorOf(NavEdgeKind.WILDERNESS, modes)
      assertEquals(255, color.alpha, "wilderness colour for $modes is translucent")
    }
  }

  @Test
  fun `every edge and node kind has a colour`() {
    // An exhaustive `when` already guarantees this at compile time; the test is here so that adding a kind and
    // reaching for `else` to make it compile fails loudly instead.
    for (kind in NavEdgeKind.entries) {
      NavGraphOverlay.colorOf(kind, setOf(MovementMode.WALK))
    }
    for (kind in NavNodeKind.entries) {
      NavGraphOverlay.colorOf(kind)
    }
  }
}

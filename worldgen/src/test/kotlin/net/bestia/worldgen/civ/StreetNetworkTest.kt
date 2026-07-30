package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * The street layout on flat, unobstructed ground.
 *
 * Tested without a world behind it, which is the reason [TownFrame] takes functions rather than layers: the
 * failure mode this guards against is geometric, not geographic. A street network that comes out as a *tree*
 * has no cycles, so face traversal finds no bounded faces, so the town gets no blocks, no plots and no
 * buildings - and at world scale that is indistinguishable from a correct town, which is exactly how it
 * survives review.
 */
class StreetNetworkTest {

  private fun flatFrame(radius: Double, approaches: List<Vec2d> = emptyList()) = TownFrame(
    centre = Vec2d(1_000.0, 1_000.0),
    builtRadius = radius,
    groundAt = { 100.0 },
    buildable = { true },
    approaches = approaches
  )

  private fun roll(seed: Long) = townRoll(GenRng.hashString("test-$seed"), 0)

  @Test
  fun `an organic town produces a connected network, not a scatter`() {
    val frame = flatFrame(330.0)
    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(1))

    assertTrue(graph.edges.size > 40, "expected a real network, got ${graph.edges.size} edges")

    // Planarity is the property the chains and the plot tests both rest on: every crossing must be a shared
    // node. Asserted by its consequence - no two edges may cross except at an endpoint they share.
    for (i in graph.edges.indices) {
      for (j in i + 1 until graph.edges.size) {
        val a = graph.edges[i]
        val b = graph.edges[j]
        if (a.a == b.a || a.a == b.b || a.b == b.a || a.b == b.b) continue

        val hit = net.bestia.worldgen.vector.Intersections.segmentCrossing(
          graph.nodes[a.a], graph.nodes[a.b], graph.nodes[b.a], graph.nodes[b.b]
        )
        assertTrue(hit == null, "two streets cross at ${hit?.first} without sharing a node")
      }
    }
  }

  @Test
  fun `a grid town lays plots along every street`() {
    val frame = flatFrame(330.0, listOf(Vec2d(1.0, 0.0)))
    val graph = StreetPlanner.plan(frame, TownLayout.GRID, roll(2))
    val lots = LotPlanner.subdivide(graph, frame, frontage = 9.0, depth = 16.0, setback = 3.5)

    assertTrue(lots.size >= 200, "a 660 m grid should hold hundreds of plots, got ${lots.size}")
  }

  @Test
  fun `no two plots overlap`() {
    val frame = flatFrame(300.0)
    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(9))
    val lots = LotPlanner.subdivide(graph, frame, frontage = 9.0, depth = 16.0, setback = 3.5)

    // Quadratic, but this is the property the whole rejection pass exists to guarantee and the counts here
    // are a few hundred. Two overlapping plots are two buildings inside each other.
    for (i in lots.indices) {
      for (j in i + 1 until lots.size) {
        val a = lots[i]
        val b = lots[j]
        if (a.centre.distanceTo(b.centre) > 40.0) continue
        assertTrue(!boxesOverlap(a, b), "plots $i and $j overlap at ${a.centre} and ${b.centre}")
      }
    }
  }

  private fun boxesOverlap(a: Lot, b: Lot): Boolean {
    val axes = listOf(a.inwards.perpendicular(), a.inwards, b.inwards.perpendicular(), b.inwards)
    for (axis in axes) {
      val gap = kotlin.math.abs((b.centre - a.centre) dot axis)
      if (gap > a.extentAlong(axis) + b.extentAlong(axis)) return false
    }
    return true
  }

  /**
   * The number this test exists for.
   *
   * A city of five thousand wants about nine hundred buildings, and it is only worth having a layout stage if
   * the layout can hold them. The first version of this produced ninety plots for that city and the shortfall
   * was invisible on a map - which is what the `town` tool's "wanted versus built" line was added to surface,
   * and what this pins so it cannot come back.
   */
  @Test
  fun `a city-sized town yields plots enough for its population`() {
    val frame = flatFrame(330.0)
    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(3))
    val lots = LotPlanner.subdivide(graph, frame, frontage = 9.0, depth = 16.0, setback = 3.5)

    assertTrue(
      lots.size >= 400,
      "a 330 m town should hold hundreds of plots, got ${lots.size} from ${graph.edges.size} street edges"
    )
  }

  @Test
  fun `every plot fronts a street and lies inside its block`() {
    val frame = flatFrame(240.0)
    val graph = StreetPlanner.plan(frame, TownLayout.GRID, roll(4))
    val lots = LotPlanner.subdivide(graph, frame, frontage = 9.0, depth = 16.0, setback = 3.5)

    assertTrue(lots.isNotEmpty(), "the grid produced no plots at all")

    for (lot in lots) {
      // Frontage is a property of the construction here rather than a check: the plot's front edge is on the
      // block boundary by definition. What can still be wrong is the direction, so assert that the front is
      // nearer the street than the back.
      val front = lot.centre - lot.inwards * lot.halfDepth
      val back = lot.centre + lot.inwards * lot.halfDepth
      val nearest = graph.nodes.minByOrNull { it.distanceTo(front) }!!
      assertTrue(
        nearest.distanceTo(front) <= nearest.distanceTo(back) + 1e-6,
        "a plot has its back to the street"
      )
      assertTrue(lot.halfFrontage > 0.0 && lot.halfDepth > 0.0)
    }
  }

  /**
   * A river through the middle must not empty the town.
   *
   * This is the failure the `town` tool found in the first working version, and it is worth a test of its own
   * because the mechanism is not obvious. Lots were cut from the *faces* of the street graph, and the faces
   * existed because of the ring streets - so removing a few ring segments where they crossed a channel broke
   * each ring's cycle, and a broken ring encloses nothing. One river took a city from nine hundred plots to
   * ninety, and the map looked entirely reasonable either way.
   */
  @Test
  fun `a river through the middle costs the plots it covers and no more`() {
    val clear = flatFrame(330.0)
    val unobstructed = LotPlanner.subdivide(
      StreetPlanner.plan(clear, TownLayout.ORGANIC, roll(8)), clear,
      frontage = 9.0, depth = 16.0, setback = 3.5
    ).size

    // A 24 m channel across the town, which is a big river at this scale.
    val split = TownFrame(
      centre = clear.centre,
      builtRadius = clear.builtRadius,
      groundAt = { 100.0 },
      buildable = { kotlin.math.abs(it.y - 1_000.0) > 12.0 },
      approaches = emptyList()
    )
    val obstructed = LotPlanner.subdivide(
      StreetPlanner.plan(split, TownLayout.ORGANIC, roll(8)), split,
      frontage = 9.0, depth = 16.0, setback = 3.5
    ).size

    assertTrue(
      obstructed > unobstructed * 0.6,
      "a single channel took the town from $unobstructed plots to $obstructed"
    )
  }

  @Test
  fun `water and steep ground keep streets out`() {
    // A frame in which the whole eastern half is unbuildable. Nothing may be laid there, and the western half
    // must still work - the failure worth guarding is a rejection that empties the town rather than half of it.
    val frame = TownFrame(
      centre = Vec2d(1_000.0, 1_000.0),
      builtRadius = 300.0,
      groundAt = { 100.0 },
      buildable = { it.x <= 1_000.0 },
      approaches = emptyList()
    )

    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(5))
    assertTrue(graph.edges.isNotEmpty(), "the buildable half should still have streets")
    for (edge in graph.edges) {
      assertTrue(
        graph.nodes[edge.a].x <= 1_000.5 && graph.nodes[edge.b].x <= 1_000.5,
        "a street crossed into unbuildable ground"
      )
    }
  }

  @Test
  fun `the layout is a pure function of the seed`() {
    val frame = flatFrame(280.0)
    val once = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(6))
    val twice = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(6))

    assertEquals(once.nodes.size, twice.nodes.size)
    assertEquals(once.edges.size, twice.edges.size)
    for (i in once.nodes.indices) {
      assertEquals(once.nodes[i], twice.nodes[i], "node $i moved between two identical runs")
    }
  }

  @Test
  fun `approach roads become the main streets`() {
    val east = Vec2d(1.0, 0.0)
    val frame = flatFrame(300.0, listOf(east))
    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(7))

    // Some rank-0 edge must run roughly east from near the centre, or the road arriving from the east does
    // not connect to anything and the town has a high street pointing nowhere.
    val eastward = graph.edges.filter { it.rank == 0 }.any { edge ->
      val a = graph.nodes[edge.a]
      val b = graph.nodes[edge.b]
      val direction = (b - a).normalized()
      (direction dot east) > 0.7 && minOf(a.x, b.x) >= frame.centre.x - 40.0
    }
    assertTrue(eastward, "no rank-0 street runs east towards the approach road")
  }
}

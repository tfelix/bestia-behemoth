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

  /**
   * A town on flat ground, with the real boundary rather than a stand-in.
   *
   * The boundary is built through [TownBoundary] and not, say, as a circle of the same radius, because it is now
   * the thing every street is clipped against - a test that handed the planner a disc would be testing the code
   * that was replaced. The axis is fixed east so that a test asserting where a street runs has something stable
   * to assert against; the elongation itself is what varies with the seed.
   */
  private fun flatFrame(
    radius: Double,
    approaches: List<Vec2d> = emptyList(),
    seed: Long = 1L,
    buildable: (Vec2d) -> Boolean = { true }
  ): TownFrame {
    val centre = Vec2d(1_000.0, 1_000.0)
    return TownFrame(
      centre = centre,
      builtRadius = radius,
      boundary = TownBoundary.of(
        centre = centre,
        builtRadius = radius,
        axis = Vec2d(1.0, 0.0),
        aspect = TownBoundary.aspectOf(roll(seed), StreetParams()),
        seed = seed,
        params = StreetParams()
      ),
      groundAt = { 100.0 },
      buildable = buildable,
      approaches = approaches
    )
  }

  private fun roll(seed: Long) = townRoll(GenRng.hashString("test-$seed"), 0)

  @Test
  fun `an organic town produces a connected network, not a scatter`() {
    val frame = flatFrame(330.0)
    val graph = StreetPlanner.plan(frame, TownLayout.ORGANIC, roll(1))

    assertTrue(graph.edges.size > 40, "expected a real network, got ${graph.edges.size} edges")
  }

  /**
   * Planarity, over enough seeds to mean something.
   *
   * This assertion used to live in the test above, on one seed, and **it was a lottery it happened to be
   * winning**. Measured across sixty seeds at each of six `minRadials` values, one pass of the planariser left
   * two to five per cent of seeds with a crossing that had no node at it - so the property held on seed 1 and
   * failed on seed 14, and which seeds failed moved whenever any parameter changed. It surfaced when
   * `StreetParams` was unified with the public mirror that had been shadowing it, purely because that shifted
   * the default `minRadials` from 4 to 3 and so redrew the lottery.
   *
   * Hence a sweep and its own test. A property that holds on ninety-seven per cent of inputs is not a property,
   * and a single-seed test cannot tell the difference.
   */
  @Test
  fun `a grown network is planar on every seed`() {
    for (minRadials in 2..7) {
      val params = StreetParams(minRadials = minRadials)
      for (seed in 1..60L) {
        val graph = StreetPlanner.plan(flatFrame(330.0), TownLayout.ORGANIC, roll(seed), params)
        assertTrue(
          !graph.hasCrossing(),
          "two streets cross without sharing a node: minRadials=$minRadials seed=$seed"
        )
      }
    }
  }

  @Test
  fun `a planned town is planar on every seed`() {
    // The grid clips to terrain and snaps, so it is exposed to the same welding displacement as the grown one.
    for (seed in 1..60L) {
      val graph = StreetPlanner.plan(flatFrame(330.0), TownLayout.GRID, roll(seed))
      assertTrue(!graph.hasCrossing(), "two streets cross without sharing a node: grid seed=$seed")
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
    val setback = 3.5
    val frame = flatFrame(240.0)
    val graph = StreetPlanner.plan(frame, TownLayout.GRID, roll(4))
    val lots = LotPlanner.subdivide(graph, frame, frontage = 9.0, depth = 16.0, setback = setback)

    assertTrue(lots.isNotEmpty(), "the grid produced no plots at all")

    for (lot in lots) {
      val front = lot.centre - lot.inwards * lot.halfDepth
      val back = lot.centre + lot.inwards * lot.halfDepth

      // Measured against street *segments*, not nodes. The nearest-node version of this held only because a grid
      // clipped to a circle has nodes everywhere: once the grid is clipped to the town's own edge its lines end
      // raggedly, so a plot at the edge can have some perpendicular street's junction as its nearest node and
      // fail a test about a property it satisfies perfectly well. A node is not a street.
      val fronting = graph.segmentsNear(front, setback + 1.0)
        .map { (a, b) -> distanceToSegment(front, a, b) to distanceToSegment(back, a, b) }
        // The street this plot fronts is `setback` away from its front edge, by construction. Anything further is
        // some other street, and anything nearer would be a street inside the setback.
        .filter { it.first <= setback + 1e-6 }

      assertTrue(fronting.isNotEmpty(), "a plot has no street within its own setback of its front edge")
      assertTrue(
        fronting.any { it.second > it.first },
        "a plot has its back to the street it fronts"
      )
      assertTrue(lot.halfFrontage > 0.0 && lot.halfDepth > 0.0)
    }
  }

  private fun distanceToSegment(p: Vec2d, a: Vec2d, b: Vec2d): Double {
    val ab = b - a
    val lengthSq = ab.lengthSquared
    if (lengthSq == 0.0) return p.distanceTo(a)
    val t = (((p - a) dot ab) / lengthSq).coerceIn(0.0, 1.0)
    return p.distanceTo(a + ab * t)
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

    // A 24 m channel across the town, which is a big river at this scale. Same boundary as `clear`, so the only
    // difference between the two towns is the water.
    val split = flatFrame(330.0, buildable = { kotlin.math.abs(it.y - 1_000.0) > 12.0 })
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
    val frame = flatFrame(300.0, buildable = { it.x <= 1_000.0 })

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

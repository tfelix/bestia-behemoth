package net.bestia.zone.navigation.region

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class RegionGridTest {

  @Test
  fun `with no world loaded it cannot tell`() {
    assertEquals(RegionGrid.Way.Unknown, RegionGrid(WaterSource.NONE).wayBetween(Vec3L(0, 0, 0), Vec3L(900, 0, 0)))
  }

  @Test
  fun `over dry land the way is straight`() {
    val way = assertIs<RegionGrid.Way.Found>(RegionGrid(water({ _, _ -> false })).wayBetween(ORIGIN, FAR_SHORE))

    assertEquals(listOf(FAR_SHORE), way.points)
  }

  @Test
  fun `a lake in between is walked round, never across`() {
    val way = assertIs<RegionGrid.Way.Found>(RegionGrid(water(::inLake)).wayBetween(ORIGIN, FAR_SHORE))

    assertEquals(FAR_SHORE, way.points.last())
    assertTrue(way.points.size > 1, "it went straight across")
    (listOf(ORIGIN) + way.points).zipWithNext().forEach { (a, b) -> assertDry(a, b) }
  }

  @Test
  fun `an island is no way at all`() {
    val ring = { x: Double, y: Double -> Math.hypot(x - 300.0, y) in 100.0..260.0 }

    assertEquals(RegionGrid.Way.None, RegionGrid(water(ring)).wayBetween(ORIGIN, Vec3L(300, 0, 0)))
  }

  @Test
  fun `a river with no bridge for kilometres cannot be crossed, and one with a bridge can`() {
    val river = { x: Double, _: Double -> x in 280.0..340.0 }
    val bridge = Vec2d(270.0, 16.0) to Vec2d(350.0, 16.0)

    assertEquals(RegionGrid.Way.None, RegionGrid(water(river)).wayBetween(ORIGIN, FAR_SHORE))
    assertIs<RegionGrid.Way.Found>(RegionGrid(water(river, listOf(bridge))).wayBetween(ORIGIN, FAR_SHORE))
  }

  @Test
  fun `a ridge with a low way round is walked round, and not straightened back over`() {
    val ridge = ridge(height = 75.0, halfLength = 300.0)
    val way = assertIs<RegionGrid.Way.Found>(RegionGrid(water(DRY, ground = ridge)).wayBetween(ORIGIN, FAR_SHORE))

    assertTrue(way.points.any { Math.abs(it.y) > 300 }, "it climbed the ridge: ${way.points}")
    (listOf(ORIGIN) + way.points).zipWithNext().forEach { (a, b) -> assertBelow(ridge, 20.0, a, b) }
  }

  @Test
  fun `a gentle ridge with no way round near is crossed`() {
    val way = assertIs<RegionGrid.Way.Found>(
      RegionGrid(water(DRY, ground = ridge(height = 10.0, halfLength = 5_000.0))).wayBetween(ORIGIN, FAR_SHORE),
    )

    assertTrue(way.points.all { Math.abs(it.y) < 64 }, "it went the long way round: ${way.points}")
  }

  @Test
  fun `a road beside the way is walked`() {
    val road = listOf(Vec2d(0.0, 48.0), Vec2d(600.0, 48.0))
    val way = assertIs<RegionGrid.Way.Found>(RegionGrid(water(DRY, roads = listOf(road))).wayBetween(ORIGIN, FAR_SHORE))

    assertTrue(way.points.any { it.y in 32..63 }, "it kept off the road: ${way.points}")
  }

  @Test
  fun `a cliff across the country is no way at all`() {
    val cliff = { x: Double, _: Double -> if (x > 300.0) 100.0 else 0.0 }

    assertEquals(RegionGrid.Way.None, RegionGrid(water(DRY, ground = cliff)).wayBetween(ORIGIN, FAR_SHORE))
  }

  @Test
  fun `a bridge to avoid sends the way to another`() {
    val river = { x: Double, _: Double -> x in 280.0..340.0 }
    val near = Vec2d(270.0, 16.0) to Vec2d(350.0, 16.0)
    val far = Vec2d(270.0, 1_000.0) to Vec2d(350.0, 1_000.0)
    val avoid = listOf(Vec3L(270, 16, 0) to Vec3L(350, 16, 0))

    val way = assertIs<RegionGrid.Way.Found>(
      RegionGrid(water(river, listOf(near, far))).wayBetween(ORIGIN, FAR_SHORE, avoid = avoid),
    )

    assertTrue(way.points.any { it.y > 900 }, "it crossed by the bridge to avoid: ${way.points}")
  }

  @Test
  fun `a search off the tick is pending until it is done, and searched once`() {
    val queued = ArrayList<Runnable>()
    val searches = WaySearches(RegionGrid(water(::inLake)), Executor { queued.add(it) })

    assertEquals(RegionGrid.Way.Pending, searches.way(ORIGIN, FAR_SHORE))
    assertEquals(RegionGrid.Way.Pending, searches.way(Vec3L(5, 5, 0), FAR_SHORE))
    assertEquals(1, queued.size, "the same way from the same cell was searched twice")

    queued.single().run()

    assertIs<RegionGrid.Way.Found>(searches.way(ORIGIN, FAR_SHORE))
    assertIs<RegionGrid.Way.Found>(searches.way(ORIGIN, FAR_SHORE), "the answer was lost once read")
    assertEquals(1, queued.size)
  }

  @Test
  fun `a click is tested where it is, not by its cell`() {
    val grid = RegionGrid(water(::inLake))

    assertTrue(grid.isWaterAt(Vec3L(300, 0, 0)))
    assertFalse(grid.isWaterAt(Vec3L(300, 210, 0)))
  }

  /** Every metre of a straight stretch is off the lake, give or take the cell a corner sits in. */
  private fun assertDry(a: Vec3L, b: Vec3L) {
    val length = Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toInt()
    for (i in 0..length) {
      val t = if (length == 0) 0.0 else i.toDouble() / length
      val x = a.x + (b.x - a.x) * t
      val y = a.y + (b.y - a.y) * t
      if (Math.hypot(x - 300.0, y) < LAKE_RADIUS - CELL_SLACK) fail("$a -> $b crosses the lake at ($x, $y)")
    }
  }

  /** No point of a straight stretch stands higher than [limit] on [ground]. */
  private fun assertBelow(ground: (Double, Double) -> Double, limit: Double, a: Vec3L, b: Vec3L) {
    val length = Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toInt()
    for (i in 0..length) {
      val t = if (length == 0) 0.0 else i.toDouble() / length
      val x = a.x + (b.x - a.x) * t
      val y = a.y + (b.y - a.y) * t
      if (ground(x, y) > limit) fail("$a -> $b climbs to ${ground(x, y)} m at ($x, $y)")
    }
  }

  private fun inLake(x: Double, y: Double) = Math.hypot(x - 300.0, y) <= LAKE_RADIUS

  /** A ridge [height] metres high along x 300, running [halfLength] north and south, with 150 m slopes. */
  private fun ridge(height: Double, halfLength: Double) = { x: Double, y: Double ->
    if (Math.abs(y) > halfLength) 0.0 else height * (1.0 - Math.abs(x - 300.0) / 150.0).coerceAtLeast(0.0)
  }

  private fun water(
    isDeep: (Double, Double) -> Boolean,
    crossings: List<Pair<Vec2d, Vec2d>> = emptyList(),
    ground: (Double, Double) -> Double = { _, _ -> 0.0 },
    roads: List<List<Vec2d>> = emptyList(),
  ) = object : WaterSource {
    override val isReady get() = true
    override fun survey(area: Aabb, exact: Boolean) = WaterSurvey(isDeep, crossings, ground, roads)
  }

  private companion object {
    val DRY = { _: Double, _: Double -> false }

    val ORIGIN = Vec3L(0, 0, 0)
    val FAR_SHORE = Vec3L(600, 0, 0)

    const val LAKE_RADIUS = 200.0

    /** A cell that is mostly dry counts as dry, so a way may clip the shore by up to a cell's half diagonal. */
    const val CELL_SLACK = 23.0
  }
}

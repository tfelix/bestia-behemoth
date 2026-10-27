package net.bestia.zone.navigation.region

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
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
  fun `a spent budget waits for the next call, which carries on where it stopped`() {
    val grid = RegionGrid(water(::inLake))
    var allowed = 1
    val budget = SurveyBudget { allowed-- > 0 }

    assertEquals(RegionGrid.Way.Pending, grid.wayBetween(ORIGIN, FAR_SHORE, budget))

    var calls = 1
    var way: RegionGrid.Way
    do {
      allowed = 1
      way = grid.wayBetween(ORIGIN, FAR_SHORE, budget)
      calls++
    } while (way == RegionGrid.Way.Pending && calls < 50)

    assertIs<RegionGrid.Way.Found>(way, "still $way after $calls calls")
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

  private fun inLake(x: Double, y: Double) = Math.hypot(x - 300.0, y) <= LAKE_RADIUS

  private fun water(isDeep: (Double, Double) -> Boolean, crossings: List<Pair<Vec2d, Vec2d>> = emptyList()) =
    object : WaterSource {
      override val isReady get() = true
      override fun survey(area: Aabb, exact: Boolean) = WaterSurvey(isDeep, crossings)
    }

  private companion object {
    val ORIGIN = Vec3L(0, 0, 0)
    val FAR_SHORE = Vec3L(600, 0, 0)

    const val LAKE_RADIUS = 200.0

    /** A cell that is mostly dry counts as dry, so a way may clip the shore by up to a cell's half diagonal. */
    const val CELL_SLACK = 23.0
  }
}

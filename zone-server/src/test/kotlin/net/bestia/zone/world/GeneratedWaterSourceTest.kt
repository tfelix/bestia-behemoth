package net.bestia.zone.world

import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.region.RegionGrid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The water grid over the dev world, at the lake south of Trestein a map click once walked straight into.
 *
 * ```
 *   ~~~~~~~~ sea ~~~~~~~        Trestein
 *   ~~~~~~~~~~~~~~                 x  <- the player stood here
 *   ~~~~~~~~~~ lake ~~~~~~~~~~~~~~~
 *        ~~~~~~~~~~~~~~~~
 *                 o  <- and clicked here, across it
 * ```
 */
class GeneratedWaterSourceTest {

  @Test
  fun `the lake south of Trestein is water and the town is not`() {
    assertTrue(grid.isWaterAt(Vec3L(34600, 31100, 0)))
    assertFalse(grid.isWaterAt(STOOD))
    assertFalse(grid.isWaterAt(CLICKED))
  }

  @Test
  fun `a click across the lake is walked round it`() {
    val way = assertIs<RegionGrid.Way.Found>(grid.wayBetween(STOOD, CLICKED))

    assertEquals(CLICKED, way.points.last())
    assertTrue(way.points.size > 1, "it went straight across")
    (listOf(STOOD) + way.points).zipWithNext().forEach { (a, b) -> assertMostlyDry(a, b) }
  }

  /**
   * At most a cell's worth of water along a stretch: a cell that is mostly dry is open, so a way may clip a shore
   * or ford a stream, but never swims the lake.
   */
  private fun assertMostlyDry(a: Vec3L, b: Vec3L) {
    val survey = water.survey(Aabb(
      minOf(a.x, b.x) - 1.0, minOf(a.y, b.y) - 1.0, maxOf(a.x, b.x) + 1.0, maxOf(a.y, b.y) + 1.0,
    ), exact = true)
    val length = Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toInt()

    var wetRun = 0
    for (i in 0..length) {
      val t = if (length == 0) 0.0 else i.toDouble() / length
      val x = a.x + (b.x - a.x) * t
      val y = a.y + (b.y - a.y) * t
      wetRun = if (survey.isDeep(x, y)) wetRun + 1 else 0
      if (wetRun > MAX_WET_RUN_METRES) fail("$a -> $b runs through ${wetRun} m of water at ($x, $y)")
    }
  }

  private companion object {
    /** `application.yml`'s pinned seed, as `TownClearanceTest` uses: the world the dev server runs. */
    const val DEV_SEED = 11_753_242L

    /** From the client log of the walk in question. */
    val STOOD = Vec3L(35392, 31416, 0)
    val CLICKED = Vec3L(34800, 30600, 0)

    const val MAX_WET_RUN_METRES = 32

    private val settings = WorldGenConfig()

    // Once per class, not per test: the world takes seconds to build. Read-only here, as everywhere.
    val water = GeneratedWaterSource.of(
      StandardWorld.build(
        WorldConfig(
          seed = DEV_SEED,
          widthCells = settings.widthCells,
          heightCells = settings.heightCells,
          baseResolution = Resolution(settings.cellSizeMetres),
          seaLevel = settings.seaLevelMetres,
          chunkSize = settings.chunkSize,
          chunkHeight = settings.chunkHeight,
          voxelSize = settings.voxelSizeMetres,
          wrapX = settings.wrapX,
          wrapY = settings.wrapY
        )
      )
    )
    val grid = RegionGrid(water)
  }
}

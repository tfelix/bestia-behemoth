package net.bestia.zone.world

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.ColumnSummary
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkCoords
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That a new master lands on dry ground inside the square around its ward stone.
 *
 * Wetness is read off materialised voxels rather than off the samplers [ArrivalScatter] uses, so the test
 * does not agree with the code merely by sharing its mistakes.
 */
class ArrivalScatterTest {

  private val generated = GeneratedWorlds.of(GeneratedWorlds.SEEDS.first())
  private val summaries = HashMap<ChunkPos, ColumnSummary>()

  @Test
  fun `a stone on the shore sends the master to dry tiles within the square`() {
    val stone = firstShoreTile()
    val wetInSquare = tilesAround(stone).count { isWet(it.first, it.second) }
    assertTrue(wetInSquare > MIN_WET_TILES, "Only $wetInSquare wet tiles around $stone - this checked nothing")

    val random = Random(7)
    val arrivals = (1..PICKS).map { ArrivalScatter.pick(generated, stone, random) }

    for (arrival in arrivals) {
      assertTrue(abs(arrival.x - stone.x) <= ArrivalScatter.RADIUS_TILES, "$arrival is too far east or west of $stone")
      assertTrue(abs(arrival.y - stone.y) <= ArrivalScatter.RADIUS_TILES, "$arrival is too far north or south of $stone")
      assertNotEquals(stone, arrival)
      assertTrue(!isWet(arrival.x, arrival.y), "$arrival stands in water")
    }

    assertTrue(arrivals.toSet().size > MIN_DISTINCT_ARRIVALS, "The arrivals are not scattered: ${arrivals.toSet()}")
  }

  @Test
  fun `a stone with no dry ground around it keeps the master on the stone`() {
    val stone = Vec3L(0, 0, 0)

    assertEquals(stone, ArrivalScatter.pick(generated, stone, Random(7)))
  }

  /** The first tile east of the western ocean margin, found by bisecting the heightfield along the middle row. */
  private fun firstShoreTile(): Vec3L {
    val config = generated.config
    val row = config.heightMetres / 2.0
    val dry = { x: Double -> generated.base.heightAt(x, row) > config.seaLevel }

    var wet = 0.0
    var land = 0.0
    while (land < config.widthMetres && !dry(land)) {
      wet = land
      land += SHORE_SEARCH_STEP_METRES
    }

    repeat(BISECTIONS) {
      val middle = (wet + land) / 2.0
      if (dry(middle)) land = middle else wet = middle
    }

    return Vec3L((land / config.voxelSize).toLong(), (row / config.voxelSize).toLong(), 0)
  }

  private fun tilesAround(stone: Vec3L): List<Pair<Long, Long>> {
    val reach = ArrivalScatter.RADIUS_TILES
    return (-reach..reach).flatMap { dx -> (-reach..reach).map { dy -> (stone.x + dx) to (stone.y + dy) } }
  }

  /** Water in either slab around sea level, which is where a shore's ground and its water both live. */
  private fun isWet(x: Long, y: Long): Boolean {
    val config = generated.config

    return (-1..0).any { slab ->
      val at = ChunkCoords.localise(config, x, y, slab.toLong() * config.chunkHeight)!!
      val summary = summaries.getOrPut(at.chunk) { ColumnSummary.of(generated.materializer.materialize(at.chunk)) }

      summary.waterHeightAt(at.localX, at.localY) >= 0.0
    }
  }

  private companion object {
    const val PICKS = 100
    const val MIN_WET_TILES = 20
    const val MIN_DISTINCT_ARRIVALS = 10
    const val SHORE_SEARCH_STEP_METRES = 8.0
    const val BISECTIONS = 24
  }
}

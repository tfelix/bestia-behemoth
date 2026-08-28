package net.bestia.worldgen.place

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Prints the shape of the partition so its constants can be chosen from measurements.
 *
 * Not an assertion of anything. `RegionKind`'s relief thresholds and `RegionParams.spacing` are numbers
 * about how a world reads, and the first guesses at both were wrong in ways no structural test could
 * see: nine regions in ten came back as fells because 700 m of relief is unremarkable on a world running
 * at a detail scale of four, and regions averaged thirteen kilometres across against a target of eight.
 *
 * Disabled because it asserts nothing and builds eight worlds to say it - a minute and a half of CI for a
 * report nobody reads until they are changing one of those constants. Enable it then; the numbers quoted
 * in `RegionParams.spacing` and `RegionKind.FELLS_RELIEF` came out of this and should be re-read here
 * rather than re-guessed. `viewer/RegionOverlay` is the other half of the same job, for what a number
 * cannot show.
 */
@Disabled("Tuning report, not a check. See the class note.")
class RegionCalibrationTest {

  @Test
  fun `report region size and kind distribution`() {
    val world = StandardWorld.build(
      WorldConfig(seed = StandardWorld.DEFAULT_SEED, widthCells = 128, heightCells = 128)
    )

    for (spacing in listOf(4_000.0, 5_000.0, 6_000.0, 8_000.0)) {
      for (relief in listOf(0.004, 0.02)) {
        val regions = PlaceRegions.of(
          world.world,
          RegionParams(spacing = spacing, reliefPenalty = relief)
        )
        val land = regions.regions.filter { !it.isWater }
        val across = land.map { sqrt(it.cellCount.toDouble()) }

        println(
          "spacing=${spacing.toInt()} relief=$relief -> ${regions.count} regions " +
              "(${regions.landCount} land), land width p50=${"%.1f".format(median(across))} km " +
              "p10=${"%.1f".format(percentile(across, 0.1))} p90=${"%.1f".format(percentile(across, 0.9))}"
        )
      }
    }

    val regions = PlaceRegions.of(world.world)
    val reliefs = regions.regions.filter { !it.isWater }.map { it.relief }.sorted()
    println(
      "land relief: p10=${percentile(reliefs, 0.1).toInt()} p25=${percentile(reliefs, 0.25).toInt()} " +
          "p50=${median(reliefs).toInt()} p75=${percentile(reliefs, 0.75).toInt()} " +
          "p90=${percentile(reliefs, 0.9).toInt()}"
    )
    println("kinds: " + regions.regions.groupingBy { it.kind }.eachCount().toList()
      .sortedByDescending { it.second })
  }

  private fun median(values: List<Double>): Double {
    return percentile(values, 0.5)
  }

  private fun percentile(values: List<Double>, share: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    return sorted[(share * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)]
  }
}

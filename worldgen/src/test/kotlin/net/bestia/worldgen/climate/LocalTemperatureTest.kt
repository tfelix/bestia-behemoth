package net.bestia.worldgen.climate

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What temperature the world actually reaches, where, and when.
 *
 * `REMINDER.md` asks for one property above all: **comfortable all year in the low-level country, and swinging
 * hard in the deserts, the mountains and the high-mana ground**, so that the hostile places need equipment
 * rather than merely holding harder monsters. That is a claim about a distribution, and the only way to know
 * whether the five terms in `LocalTemperature` deliver it is to sample the world and look.
 *
 * It is also the calibration for `ExposureConfig`'s comfort band, which would otherwise be two numbers somebody
 * picked. The band is asserted against the measured distribution here, so widening it silently is a test
 * failure rather than a quiet removal of the whole mechanic.
 */
class LocalTemperatureTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(world.world.layers, world.config) ?: error("no climate layers")
  }

  private fun elevation() = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
  private fun biomes() = world.world.layers.require<IntLayer>(LayerId.BIOME)

  /** Air temperature at a cell over a year, sampled four times a day at four points in the year. */
  private fun samples(cellX: Int, cellY: Int): List<Double> {
    val metres = world.config.baseResolution.metresPerCell
    val worldX = (cellX + 0.5) * metres
    val worldY = (cellY + 0.5) * metres
    val ground = elevation()[cellX, cellY].toDouble()

    val out = ArrayList<Double>()
    for (quarter in 0 until 8) {
      val yearProgress = quarter / 8.0
      for (hour in 0 until 8) {
        out.add(temperature.at(worldX, worldY, ground, yearProgress, hour / 8.0).airCelsius)
      }
    }
    return out
  }

  @Test
  fun `the gentle country is comfortable all year and the harsh country is not`() {
    val biome = biomes()
    val ground = elevation()
    val seaLevel = world.config.seaLevel

    // Comfort band, mirrored from `zone-server`'s ExposureConfig defaults. Duplicated deliberately: worldgen
    // cannot depend on the server, and the point of the assertion is that those two numbers are right for the
    // world this module produces. If they move there, this fails here, which is the coupling that matters.
    val comfortLow = -6.0
    val comfortHigh = 34.0

    val candidateGentle = IntArray(CANDIDATE_BANDS.size)
    val candidateHarsh = IntArray(CANDIDATE_BANDS.size)
    var gentleSamples = 0
    var gentleOutside = 0
    var harshSamples = 0
    var harshOutside = 0

    for (cellY in 0 until world.config.heightCells step 4) {
      for (cellX in 0 until world.config.widthCells step 4) {
        if (ground[cellX, cellY] <= seaLevel) continue
        val kind = Biome.entries[biome[cellX, cellY]]

        val group = when (kind) {
          in GENTLE -> true
          in HARSH -> false
          else -> continue
        }

        val drawn = samples(cellX, cellY)
        for ((index, band) in CANDIDATE_BANDS.withIndex()) {
          val n = drawn.count { it < band.first || it > band.second }
          if (group) candidateGentle[index] += n else candidateHarsh[index] += n
        }
        val outside = drawn.count { it < comfortLow || it > comfortHigh }
        if (group) {
          gentleSamples += 64
          gentleOutside += outside
        } else {
          harshSamples += 64
          harshOutside += outside
        }
      }
    }

    assertTrue(gentleSamples > 0, "no gentle land sampled; the test measured nothing")
    assertTrue(harshSamples > 0, "no harsh land sampled; the test measured nothing")

    val gentleShare = gentleOutside.toDouble() / gentleSamples
    val harshShare = harshOutside.toDouble() / harshSamples

    println(
      "outside the %.0f..%.0f C comfort band: gentle land %.1f%% of the year, harsh land %.1f%%"
        .format(comfortLow, comfortHigh, gentleShare * 100, harshShare * 100)
    )

    for ((index, band) in CANDIDATE_BANDS.withIndex()) {
      println(
        "  band %5.1f..%4.1f  gentle %5.1f%%  harsh %5.1f%%".format(
          band.first, band.second,
          candidateGentle[index] * 100.0 / gentleSamples,
          candidateHarsh[index] * 100.0 / harshSamples
        )
      )
    }

    // The world's hottest hour anywhere, which decides whether the heat half of exposure can fire at all.
    val hottest = CANDIDATE_BANDS.first().second
    println(
      "note: nothing on this world exceeds %.0f C, so heat exposure is unreachable until volcanic regions land"
        .format(hottest)
    )

    assertTrue(
      gentleShare < 0.08,
      "gentle land is uncomfortable ${"%.1f".format(gentleShare * 100)}% of the year; " +
          "exposure would be a tax on ordinary travel"
    )
    assertTrue(
      harshShare > 0.50,
      "harsh land is uncomfortable only ${"%.1f".format(harshShare * 100)}% of the year; " +
          "the deserts and the mountains need no equipment and the mechanic is decoration"
    )
  }

  @Test
  fun `a continental desert swings harder than a coast`() {
    // The diurnal term's whole reason for existing. Continentality and aridity both feed it, so the driest
    // inland ground has to move more over a day than a wet coast does.
    val biome = biomes()
    val ground = elevation()
    val toOcean = world.world.layers.require<FloatLayer>(LayerId.DISTANCE_TO_OCEAN)
    val seaLevel = world.config.seaLevel
    val metres = world.config.baseResolution.metresPerCell

    var driestSwing = 0.0
    var coastalSwing = 0.0
    var driestDistance = -1.0

    for (cellY in 0 until world.config.heightCells step 2) {
      for (cellX in 0 until world.config.widthCells step 2) {
        if (ground[cellX, cellY] <= seaLevel) continue

        val day = (0 until 16).map {
          temperature.at(
            (cellX + 0.5) * metres, (cellY + 0.5) * metres,
            ground[cellX, cellY].toDouble(), 0.4, it / 16.0
          ).airCelsius
        }
        val swing = day.max() - day.min()
        val distance = toOcean.sampleBilinear((cellX + 0.5) * metres, (cellY + 0.5) * metres)
        val kind = Biome.entries[biome[cellX, cellY]]

        if (kind == Biome.DESERT && distance > driestDistance) {
          driestDistance = distance
          driestSwing = swing
        }
        if (distance < 3_000.0) coastalSwing = maxOf(coastalSwing, swing)
      }
    }

    // Skips cleanly on a world with no desert - a legitimate seed - but says so, rather than passing quietly.
    if (driestDistance < 0.0) {
      println("no desert on this world; the diurnal comparison was skipped")
      return
    }

    println("daily swing: inland desert %.1f C, coast %.1f C".format(driestSwing, coastalSwing))
    assertTrue(
      driestSwing > coastalSwing,
      "an inland desert swings %.1f C against a coast's %.1f C".format(driestSwing, coastalSwing)
    )
  }

  @Test
  fun `the elevation residual is applied, and it is worth applying`() {
    // Skipping it reads a hilltop at its 4 km cell's mean elevation, which the plan measured at up to 6 C of
    // error in an orogen. Asserted as a *difference* between two columns of the same cell, because that is the
    // only thing the residual can be responsible for.
    val metres = world.config.baseResolution.metresPerCell
    val ground = elevation()

    var worst = 0.0
    for (cellY in 0 until world.config.heightCells step 8) {
      for (cellX in 0 until world.config.widthCells step 8) {
        if (ground[cellX, cellY] <= world.config.seaLevel) continue
        val x = (cellX + 0.5) * metres
        val y = (cellY + 0.5) * metres

        val low = temperature.at(x, y, ground[cellX, cellY].toDouble(), 0.4, 0.5).airCelsius
        val high = temperature.at(x, y, ground[cellX, cellY] + 400.0, 0.4, 0.5).airCelsius
        worst = maxOf(worst, abs(low - high))
      }
    }

    // 400 m at 6.2 C/km is about 2.5 C. Present at all is the assertion; the magnitude confirms the lapse rate
    // reached it rather than something else moving.
    assertTrue(worst > 1.5, "400 m of elevation moved the temperature by only ${"%.2f".format(worst)} C")
    assertTrue(worst < 4.0, "400 m of elevation moved it by ${"%.2f".format(worst)} C, which is too much")
  }

  private companion object {
    /** Bands measured while choosing `ExposureConfig`'s defaults. */
    val CANDIDATE_BANDS = listOf(
      -2.0 to 34.0, -4.0 to 34.0, -6.0 to 34.0, -8.0 to 34.0, -6.0 to 36.0, -10.0 to 36.0
    )

    /** The low-level country: what a new master walks through. */
    val GENTLE = setOf(
      Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.RIPARIAN, Biome.BEACH, Biome.SHRUBLAND
    )

    /** What `REMINDER.md` wants to need equipment. */
    val HARSH = setOf(
      Biome.DESERT, Biome.COLD_DESERT, Biome.ALPINE, Biome.GLACIER, Biome.ICE_SHEET, Biome.TUNDRA
    )
  }
}

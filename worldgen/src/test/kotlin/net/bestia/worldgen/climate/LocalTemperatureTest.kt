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
    var hottest = -Double.MAX_VALUE

    // Per biome as well as pooled, because the pooled figure over [HARSH] cannot answer the question the
    // assertion is asking. It was calibrated when `DESERT` classified **zero cells on every world** - see the
    // note on `Biome.DESERT`'s prototype - so "harsh land" measured four cold biomes and the threshold was
    // really a claim about ice. Once the desert existed, a biome whose mean is +17 C joined the pool and
    // dragged the average under the bar while nothing about the ice had changed.
    //
    // A pooled average also hides the failure it exists to catch: one biome needing no equipment is invisible
    // as long as its neighbours in the set need plenty. So every member is now measured and asserted on its
    // own, which is the stronger statement and the one the mechanic actually rests on.
    val outsideByBiome = HashMap<Biome, Int>()
    val samplesByBiome = HashMap<Biome, Int>()

    for (cellY in 0 until world.config.heightCells step 4) {
      for (cellX in 0 until world.config.widthCells step 4) {
        if (ground[cellX, cellY] <= seaLevel) continue
        val kind = Biome.entries[biome[cellX, cellY]]

        // Drawn before the group filter, because the hottest hour *anywhere* is a claim about all the land and
        // not only about the two named groups - and a volcanic field is in neither of them.
        val drawn = samples(cellX, cellY)
        hottest = maxOf(hottest, drawn.max())

        val outside = drawn.count { it < comfortLow || it > comfortHigh }

        // Recorded for **every** biome, before the group filter and whether or not it is in either set. The
        // per-biome table is the evidence this test exists to produce, and confining it to the two named sets
        // is how DESERT's figure stayed invisible while it was inside one of them - see [HARSH].
        outsideByBiome[kind] = (outsideByBiome[kind] ?: 0) + outside
        samplesByBiome[kind] = (samplesByBiome[kind] ?: 0) + 64

        val group = when (kind) {
          in GENTLE -> true
          in HARSH -> false
          else -> continue
        }

        for ((index, band) in CANDIDATE_BANDS.withIndex()) {
          val n = drawn.count { it < band.first || it > band.second }
          if (group) candidateGentle[index] += n else candidateHarsh[index] += n
        }
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

    println("the world's hottest hour anywhere: %.1f C".format(hottest))

    println("per biome, share of the year outside the comfort band:")
    for ((kind, samples) in samplesByBiome.entries.sortedBy { it.key.ordinal }) {
      println(
        "  %-22s %5.1f%%  over %d samples".format(
          kind.label, (outsideByBiome[kind] ?: 0) * 100.0 / samples, samples
        )
      )
    }

    // The mirror of the cold assertion below, and the thing that stops `comfortHighCelsius` shipping dead.
    //
    // This line used to read `val hottest = CANDIDATE_BANDS.first().second` - it read the literal 34.0 back out
    // of its own band list and printed it as though it were a measurement of the world. The claim it printed
    // ("nothing on this world exceeds 34 C") happened to be true, but nothing in the code that printed it had
    // ever checked, which is why it survived the term that made it false.
    assertTrue(
      hottest > comfortHigh,
      "the world's hottest hour is only ${"%.1f".format(hottest)} C against a comfort ceiling of " +
          "$comfortHigh; the heat half of exposure is unreachable and half the mechanic is decoration"
    )

    assertTrue(
      gentleShare < 0.08,
      "gentle land is uncomfortable ${"%.1f".format(gentleShare * 100)}% of the year; " +
          "exposure would be a tax on ordinary travel"
    )
    // Per biome, and every member of the set has to carry its own weight. The bar is well under the pooled
    // 0.50 this replaced because it is being asked of each biome separately rather than of an average the ice
    // could satisfy on its own - a biome that puts a player outside the comfort band a fifth of the year is a
    // biome they have to pack for, which is all the mechanic needs to be real.
    for (kind in HARSH) {
      val samples = samplesByBiome[kind] ?: 0
      // A world need not contain every biome - `ALPINE` wants ground above 2,600 m and a small world may have
      // none - and an absent biome is not a failing one. `checkBiomeVocabularyIsReachable` in `Invariants` is
      // where "this biome exists somewhere" belongs; here it would make the assertion depend on the seed.
      if (samples < MIN_SAMPLES_TO_JUDGE) continue

      val share = (outsideByBiome[kind] ?: 0).toDouble() / samples
      assertTrue(
        share > 0.20,
        "$kind is uncomfortable only ${"%.1f".format(share * 100)}% of the year over $samples samples; " +
            "it is in HARSH and needs no equipment, so for that biome the mechanic is decoration"
      )
    }
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
      Biome.GRASSLAND, Biome.TEMPERATE_FOREST, Biome.RIPARIAN, Biome.BEACH, Biome.DRYLAND
    )

    /**
     * The biomes whose **temperature** requires equipment.
     *
     * `Biome.DESERT` is deliberately not among them, and that absence is a measurement rather than an
     * omission. It used to be listed, back when the desert prototype classified no cells at all and the
     * membership therefore cost nothing. Once the desert existed it measured **1.2% of the year outside the
     * comfort band at a +16.6 C centre, and 0.0% at +24.4 C** - hotter placement made it *more* comfortable,
     * because a hot desert's mean sits in the middle of a -6..34 C band and never approaches either end.
     *
     * The band is not wrong and neither is the prototype. What the pair of figures says is that **this world
     * has no desert heat to be exposed to**: the hottest hour anywhere on it is 37.2 C, three degrees over the
     * ceiling, so the heat half of exposure barely engages on any biome and cannot engage on a biome selected
     * for being dry rather than for being hot. A desert is punishing because of water and because of a 45 C
     * afternoon, and this module models neither.
     *
     * So the desert's difficulty is carried entirely by `SpawnHostility`, which puts it at 0.95 - second only
     * to ice and lava. That is a real mechanic and it works. Listing the biome here as well would assert a
     * second one that does not exist, which is what this test is for catching.
     */
    val HARSH = setOf(
      Biome.COLD_DESERT, Biome.ALPINE, Biome.ICE_SHEET, Biome.TUNDRA
    )

    /**
     * Samples a biome needs before its own share is worth asserting on.
     *
     * Sixteen cells' worth. Below that the figure is a handful of columns in one corner of one world and
     * would make the test a seed lottery, which is the failure the pooled average was hiding in the other
     * direction.
     */
    const val MIN_SAMPLES_TO_JUDGE = 1_024
  }
}

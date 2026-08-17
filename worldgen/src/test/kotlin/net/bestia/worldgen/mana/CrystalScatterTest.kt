package net.bestia.worldgen.mana

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.PropKind
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Crystals per hectare, on corrupted ground and on clean.
 *
 * A ratio and two absolute rates, not a "some exist" check. `manaFloor` set above what the field reaches
 * would place none at all and every other test would still pass; a density set the same on both would place
 * plenty and make a corrupted province indistinguishable from a meadow. Neither is visible without counting.
 *
 * Counted through `CrystalScatter.columnAt` per voxel column rather than by materialising chunks, because
 * that is the only way to sample enough ground for a rate to mean anything - a chunk is 32 m across and the
 * clean-land spacing is 175 m, so a whole chunk holds about one crystal.
 */
class CrystalScatterTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  @Test
  fun `corrupted ground grows far more crystal than clean ground, and clean ground grows some`() {
    val corrupted = crystalsPerHectare(highCorruption = true)
    val clean = crystalsPerHectare(highCorruption = false)

    println("crystals per hectare: corrupted %.2f, clean %.2f".format(corrupted, clean))

    assertTrue(
      clean > 0.0,
      "no crystal at all on high-mana clean ground - a low-level player has nothing to collect"
    )
    assertTrue(
      corrupted > clean * 5.0,
      "corrupted ground grows %.1f per hectare against %.1f on clean; the two read as the same country"
        .format(corrupted, clean)
    )
  }

  /**
   * Crystals per hectare over several squares of corrupted, or clean, high-mana land.
   *
   * **Pooled over several squares rather than measured on one**, because one square is a biome lottery: the
   * most corrupted cell on a world is often alpine or under year-round snow, where nothing roots at all, and
   * a single sample there reports a density of zero for a reason that has nothing to do with the density.
   * The first version of this measured exactly that and read 0.67 against an intended 9.9.
   */
  private fun crystalsPerHectare(highCorruption: Boolean): Double {
    val extent = world.config.chunkExtent

    var crystals = 0
    var hectares = 0.0

    for ((originX, originY) in sampleOrigins(highCorruption, SAMPLE_SQUARES)) {
      // Whole chunks rather than a 220 m square of columns, because `propsIn` answers per chunk. Seven
      // squared covers 224 m, which is the same ground the column sweep used to walk.
      val fromChunkX = Math.floorDiv(Math.floor(originX / world.config.voxelSize).toLong(), world.config.chunkSize.toLong()).toInt()
      val fromChunkY = Math.floorDiv(Math.floor(originY / world.config.voxelSize).toLong(), world.config.chunkSize.toLong()).toInt()

      for (offsetY in 0 until SAMPLE_CHUNKS) {
        for (offsetX in 0 until SAMPLE_CHUNKS) {
          val props = world.propsIn(fromChunkX + offsetX, fromChunkY + offsetY)
          for (i in props.indices) if (props.kindAt(i) == PropKind.MANA_CRYSTAL) crystals++
          hectares += extent * extent / 10_000.0
        }
      }
    }

    return crystals / hectares
  }

  /**
   * South-west corners of [squares] sample cells, spread evenly through the qualifying population.
   *
   * Water and the biomes nothing roots in are skipped here rather than left to the scatter, so the
   * denominator is ground a crystal could stand on. Without that the measurement is a map of where the snow
   * line falls.
   *
   * ### Spread by stride, and it used to be the top N by score
   *
   * Both scores this could rank by are **saturated**, which is what made a top-N slice the wrong sampler.
   * `CORRUPTION` clamps at 1.0 and `MANA_DENSITY` is a *percentile rank*, so on the reference world every one
   * of the top cells scores exactly 1.000 - and "the six best" was really "the six lowest-indexed members of a
   * run of several hundred ties". Any change anywhere that added or removed a single cell from the candidate
   * set slid that window along and remeasured six different squares.
   *
   * It slid when the volcanic biomes landed, and the ratio it reported fell from comfortably over five to 4.2
   * while the world's actual crystal density had not moved at all: pooled over the *whole* qualifying
   * population the same world reads 5.32 per hectare corrupted against 0.75 clean, a ratio of 7.1. So the test
   * was measuring a lucky slice, and this is the sampler that measures the population instead.
   *
   * The clean side keeps an explicit [HIGH_MANA] floor rather than relying on the ranking to find high ground,
   * because that was the one thing the old ordering did usefully - a clean sample below `manaFloor` measures
   * the floor rather than the clean-land rate.
   */
  private fun sampleOrigins(highCorruption: Boolean, squares: Int): List<Pair<Double, Double>> {
    val mana = world.world.layers.require<FloatLayer>(LayerId.MANA_DENSITY)
    val corruption = world.world.layers.require<FloatLayer>(LayerId.CORRUPTION)
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val waterLevel = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val biome = world.world.layers.require<net.bestia.worldgen.core.IntLayer>(LayerId.BIOME)
    val seaLevel = world.config.seaLevel
    val metres = world.config.baseResolution.metresPerCell
    val width = mana.region.width

    val qualifying = ArrayList<Int>()
    for (i in mana.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      if (!waterLevel.data[i].isNaN()) continue

      val cellX = mana.region.minX + i % width
      val cellY = mana.region.minY + i / width
      val kind = net.bestia.worldgen.bio.Biome.entries[biome[cellX, cellY]]
      if (kind in BARREN) continue

      val isCorrupted = corruption.data[i] >= CorruptionStage.CORRUPTED
      if (isCorrupted != highCorruption) continue
      if (!highCorruption && mana.data[i] < HIGH_MANA) continue

      qualifying.add(i)
    }

    check(qualifying.size >= squares) {
      "only ${qualifying.size} usable ${if (highCorruption) "corrupted" else "clean"} cells on this world"
    }

    // Evenly through the list rather than off the front of it, so the sample is spread across every province
    // that qualifies and one cell entering or leaving the set moves at most one square.
    val stride = qualifying.size / squares
    return (0 until squares)
      .map { qualifying[it * stride] }
      .map { index ->
        // Centred on the cell, not anchored at its corner. A 220 m square from the corner spends most of its
        // area where the bilinear corruption sample is already blending toward the neighbouring cell, so it
        // measures the fringe of a province rather than its interior.
        val centreX = (mana.region.minX + index % width + 0.5) * metres
        val centreY = (mana.region.minY + index / width + 0.5) * metres
        (centreX - HALF_SQUARE) to (centreY - HALF_SQUARE)
      }
  }

  private companion object {

    /**
     * Sample squares per side, and it was six until a reseed of the world caught it out.
     *
     * The stride sampler above fixed *which* squares are drawn; this fixes how many, and the two failures are
     * different. Six squares came back on one seed at 4.85 corrupted against 1.46 clean - a ratio of 3.3,
     * under the five this asserts - and nothing about the crystals had changed. Measured on the same world at
     * more squares: 7.7 at twelve, 12.6 at twenty-four, 15.7 at forty-eight.
     *
     * **The clean side is what is noisy, not the corrupted one.** Corrupted ground reads 4.9 to 7.0 across
     * every sample size, because a corrupted province is large and uniform; clean high-mana ground reads 1.46,
     * 0.85, 0.56, 0.43 as the squares go up, because at the clean spacing of 175 m a 224 m square holds one or
     * two crystals and six of them is a dozen crystals deciding the denominator of the ratio. Over eight seeds
     * at twenty-four squares the ratio runs 7.8 to 36.7, and every one of them clears five with room; at six it
     * ran 3.3 to 35.1 on the same worlds.
     *
     * Twenty-four costs about a second per side and buys a measurement that a downstream reseed cannot flip.
     */
    const val SAMPLE_SQUARES = 24

    /** Half the sample square's edge, in metres. */
    const val HALF_SQUARE = 110.0

    /** Chunks per axis sampled around each origin. Seven at 32 m is 224 m, matching the old column sweep. */
    const val SAMPLE_CHUNKS = 7

    /** Mana a clean sample cell needs, so it measures the clean-land rate rather than `manaFloor`. */
    const val HIGH_MANA = 0.9f

    /**
     * Biomes nothing roots in, skipped so the measurement is not a map of the snow line.
     *
     * `VOLCANIC_FIELD` is deliberately **not** here even though it carries no soil and no litter. The scatter
     * vetoes an ice or snow cap and nothing else, so a crystal really does grow out of bare basalt - which is
     * both what the code does and a fine thing for it to do - and excluding it would be this test disagreeing
     * with the thing it measures.
     */
    val BARREN = setOf(
      net.bestia.worldgen.bio.Biome.ICE_SHEET,
      net.bestia.worldgen.bio.Biome.ALPINE
    )
  }
}

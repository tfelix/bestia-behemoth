package net.bestia.worldgen.mana

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.StructureSpans
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
    val voxel = world.config.voxelSize
    val side = 220
    val spans = StructureSpans()

    var crystals = 0
    var hectares = 0.0

    for ((originX, originY) in sampleOrigins(highCorruption, squares = 6)) {
      for (row in 0 until side) {
        for (column in 0 until side) {
          val worldX = originX + (column + 0.5) * voxel
          val worldY = originY + (row + 0.5) * voxel
          val ground = world.base.heightAt(worldX, worldY)

          spans.clear()
          world.materializer.crystals.columnAt(worldX, worldY, ground, spans)

          for (i in 0 until spans.count) {
            val block = spans.blockOf(i)
            if (block == BlockType.MANA_CRYSTAL_SMALL.id || block == BlockType.MANA_CRYSTAL_LARGE.id) {
              crystals++
            }
          }
        }
      }
      hectares += (side * voxel) * (side * voxel) / 10_000.0
    }

    return crystals / hectares
  }

  /**
   * South-west corners of the [squares] best sample cells: most corrupted, or highest-mana uncorrupted.
   *
   * Water and the two biomes nothing roots in are skipped here rather than left to the scatter, so the
   * denominator is ground a crystal could stand on. Without that the measurement is a map of where the snow
   * line falls.
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

    val ranked = ArrayList<Pair<Int, Double>>()
    for (i in mana.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      if (!waterLevel.data[i].isNaN()) continue

      val cellX = mana.region.minX + i % width
      val cellY = mana.region.minY + i / width
      val kind = net.bestia.worldgen.bio.Biome.entries[biome[cellX, cellY]]
      if (kind in BARREN) continue

      val isCorrupted = corruption.data[i] >= CorruptionStage.CORRUPTED
      if (isCorrupted != highCorruption) continue

      // Ranked by corruption where there is some, by mana where there is none - the clean sample has to sit
      // above `manaFloor` or it measures the floor rather than the clean-land rate.
      ranked.add(i to if (highCorruption) corruption.data[i].toDouble() else mana.data[i].toDouble())
    }

    check(ranked.size >= squares) {
      "only ${ranked.size} usable ${if (highCorruption) "corrupted" else "clean"} cells on this world"
    }

    return ranked
      .sortedByDescending { it.second }
      .take(squares)
      .map { (index, _) ->
        // Centred on the cell, not anchored at its corner. A 220 m square from the corner spends most of its
        // area where the bilinear corruption sample is already blending toward the neighbouring cell, so it
        // measures the fringe of a province rather than its interior.
        val centreX = (mana.region.minX + index % width + 0.5) * metres
        val centreY = (mana.region.minY + index / width + 0.5) * metres
        (centreX - HALF_SQUARE) to (centreY - HALF_SQUARE)
      }
  }

  private companion object {
    /** Half the sample square's edge, in metres. */
    const val HALF_SQUARE = 110.0

    /** Biomes nothing roots in, skipped so the measurement is not a map of the snow line. */
    val BARREN = setOf(
      net.bestia.worldgen.bio.Biome.ICE_SHEET,
      net.bestia.worldgen.bio.Biome.ALPINE
    )
  }
}

package net.bestia.worldgen.mana

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.PropInstances
import net.bestia.worldgen.voxel.PropKind
import net.bestia.worldgen.voxel.PropSite
import net.bestia.worldgen.voxel.StructureSpans
import kotlin.test.Test
import kotlin.test.assertEquals
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
   * The two representations of a crystal find the same crystals.
   *
   * The whole point of letting the voxel path and the prop path coexist for a while: they are driven by one
   * `crystalAt`, and this is what says so. Compared as **sets of voxel columns**, not as counts - a count
   * agrees by accident whenever two disagreements cancel, and the failure this would actually catch is a
   * lattice or ownership change that moves crystals rather than losing them.
   *
   * Both sides are given the same ground function, so the only thing that can differ is which cells each
   * decides hold a crystal.
   */
  @Test
  fun `the prop path finds exactly the crystals the voxel path draws`() {
    val voxel = world.config.voxelSize
    val size = world.config.chunkSize
    val crystals = world.materializer.crystals
    val site = PropSite { x, y -> world.base.heightAt(x, y) }
    val spans = StructureSpans()

    var chunksWithCrystals = 0
    var found = 0

    for ((originX, originY) in sampleOrigins(highCorruption = true, squares = 6)) {
      val baseX = Math.floorDiv((originX / voxel).toLong(), size.toLong()).toInt()
      val baseY = Math.floorDiv((originY / voxel).toLong(), size.toLong()).toInt()

      // A three-by-three block, so a crystal sitting on a shared chunk edge is claimed by one side
      // only - and enough chunks that a corrupted 32 m chunk's expected 0.8 crystals adds up to a sample.
      for (offsetY in 0 until 3) {
        for (offsetX in 0 until 3) {
          val chunk = ChunkPos(baseX + offsetX, baseY + offsetY)

          val fromVoxels = HashSet<Pair<Long, Long>>()
          for (localY in 0 until size) {
            for (localX in 0 until size) {
              val (worldX, worldY) = world.config.columnCenter(chunk, localX, localY)

              spans.clear()
              crystals.columnAt(worldX, worldY, world.base.heightAt(worldX, worldY), spans)

              for (i in 0 until spans.count) {
                val block = spans.blockOf(i)
                if (block != BlockType.MANA_CRYSTAL_SMALL.id && block != BlockType.MANA_CRYSTAL_LARGE.id) continue
                fromVoxels.add(
                  Math.floorDiv((worldX / voxel).toLong(), 1L) to Math.floorDiv((worldY / voxel).toLong(), 1L)
                )
              }
            }
          }

          val props = PropInstances()
          crystals.propsIn(chunk, site, props)

          val fromProps = HashSet<Pair<Long, Long>>()
          for (i in props.indices) {
            assertTrue(props.kindAt(i) == PropKind.MANA_CRYSTAL, "the crystal scatter emitted ${props.kindAt(i)}")
            fromProps.add(
              Math.floorDiv((props.xAt(i) / voxel).toLong(), 1L) to
                  Math.floorDiv((props.yAt(i) / voxel).toLong(), 1L)
            )
          }

          assertEquals(fromVoxels, fromProps, "the two paths disagree about the crystals in $chunk")

          if (fromProps.isNotEmpty()) chunksWithCrystals++
          found += fromProps.size
        }
      }
    }

    assertTrue(chunksWithCrystals > 8, "only $chunksWithCrystals of ${6 * 9} chunks held a crystal at all")
    assertTrue(found > 20, "only $found crystals compared, which asserts little")
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
    /** Half the sample square's edge, in metres. */
    const val HALF_SQUARE = 110.0

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

package net.bestia.worldgen.mana

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Corruption reaches the voxels a player stands on.
 *
 * The gap this closes is the complete-tested-never-reached shape: `CorruptionStage` can hit its target, the
 * exported PNG can look right, `SurfaceCover.blight` can be unit-tested, and **not one blighted block need
 * exist in the world** - because the dither compares the wrong way round, or because the layer never reaches
 * the sampler, or because the materialiser passes `false`. Every one of those is invisible above the chunk
 * tier. So this materialises real chunks over real corrupted ground and counts what comes out.
 */
class BlightedCoverTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  @Test
  fun `corrupted ground materialises with blighted cover`() {
    val chunk = materialiseOverCorruption()
    val counts = countCover(chunk)

    val blighted = counts.filterKeys { it in BLIGHTED }.values.sum()
    assertTrue(blighted > 0, "no blighted block in a chunk over corrupted ground; cover was $counts")

    // Most of it, not merely some. A handful would mean the dither is firing at the wrong rate, which is a
    // different bug from it not firing at all and would pass a bare "greater than zero".
    val clean = counts.filterKeys { it in CLEAN_TWINS }.values.sum()
    assertTrue(
      blighted > clean,
      "only $blighted of ${blighted + clean} cover blocks are blighted deep inside a province"
    )
  }

  @Test
  fun `clean ground materialises with no blighted cover at all`() {
    // The control. Without it "blighted blocks exist" is satisfied by a materialiser that blights the whole
    // world, which would look entirely convincing in a screenshot of a corrupted province.
    val chunk = materialiseOverCleanGround()
    val counts = countCover(chunk)

    val blighted = counts.filterKeys { it in BLIGHTED }.values.sum()
    assertTrue(blighted == 0, "blighted blocks on ground with no corruption; cover was $counts")
  }

  /** A chunk whose centre sits at the highest corruption on the world. */
  private fun materialiseOverCorruption(): VoxelChunk = materialiseAt(extremeCorruptionCell(highest = true))

  /** A chunk on dry land with no corruption at all. */
  private fun materialiseOverCleanGround(): VoxelChunk = materialiseAt(extremeCorruptionCell(highest = false))

  private fun materialiseAt(cell: Pair<Int, Int>): VoxelChunk {
    val config = world.config
    val metres = config.baseResolution.metresPerCell
    val worldX = (cell.first + 0.5) * metres
    val worldY = (cell.second + 0.5) * metres

    val chunkX = Math.floorDiv(worldX.toInt(), config.chunkExtent.toInt())
    val chunkY = Math.floorDiv(worldY.toInt(), config.chunkExtent.toInt())
    val height = world.base.heightAt(worldX, worldY)

    return world.materializer.materialize(ChunkPos(chunkX, chunkY, config.chunkZOf(height)))
  }

  /**
   * The most, or least, corrupted **dry land** cell on the world.
   *
   * Dry land explicitly: the least corrupted cell on any world is a random patch of sea floor, and a chunk
   * there has no cover on it to be blighted or otherwise, so the control would pass without testing anything.
   */
  private fun extremeCorruptionCell(highest: Boolean): Pair<Int, Int> {
    val corruption = world.world.layers.require<FloatLayer>(LayerId.CORRUPTION)
    val elevation = world.world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val waterLevel = world.world.layers.require<FloatLayer>(LayerId.WATER_LEVEL)
    val seaLevel = world.config.seaLevel

    var bestIndex = -1
    var best = if (highest) -1.0f else Float.MAX_VALUE

    for (i in corruption.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      if (!waterLevel.data[i].isNaN()) continue
      val value = corruption.data[i]
      if (highest && value > best) {
        best = value
        bestIndex = i
      } else if (!highest && value < best) {
        best = value
        bestIndex = i
      }
    }

    check(bestIndex >= 0) { "the world has no dry land" }
    check(if (highest) best >= CorruptionStage.CORRUPTED else best <= 0.0) {
      "expected an extreme, found $best - the corruption field is not what this test assumes"
    }

    val width = corruption.region.width
    return (corruption.region.minX + bestIndex % width) to (corruption.region.minY + bestIndex / width)
  }

  /** Cover blocks in a chunk, by material. Only the cover; the rock column below is not this test's business. */
  private fun countCover(chunk: VoxelChunk): Map<BlockType, Int> {
    val counts = HashMap<BlockType, Int>()
    for (block in chunk.blocks) {
      val type = BlockType.ofOrNull(block.toInt() and 0xFF) ?: continue
      if (type in BLIGHTED || type in CLEAN_TWINS) counts.merge(type, 1, Int::plus)
    }
    return counts
  }

  private companion object {
    val BLIGHTED = setOf(
      BlockType.BLIGHTED_GRASS,
      BlockType.BLIGHTED_DIRT,
      BlockType.BLIGHTED_SAND,
      BlockType.BLIGHTED_PEAT
    )

    val CLEAN_TWINS = setOf(
      BlockType.GRASS,
      BlockType.DIRT,
      BlockType.SAND,
      BlockType.PEAT
    )
  }
}

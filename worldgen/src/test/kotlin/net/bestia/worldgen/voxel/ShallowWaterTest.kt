package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Water a fraction of a voxel deep is still written.
 *
 * By the centre rule alone a bed less than half a voxel under the waterline rounded up into the fluid's top
 * voxel and the fluid rounded away, so a gently shelving shore came out as a strand of dry ground below sea
 * level. On hand-built columns for [BareRockTest]'s reason: what is under test is one column's rounding, and a
 * ramp through the waterline puts every fraction of a voxel under it in a single chunk.
 */
class ShallowWaterTest {

  @Test
  fun `every column whose ground is below the water ends in water at the waterline`() {
    val chunk = materializer().materialize(ChunkPos(0, 0, 0))
    var submerged = 0

    for (localX in 0 until CHUNK_SIZE) {
      val ground = groundAt(localX)
      if (ground >= WATER) continue
      submerged++

      val offset = chunk.columnOffset(localX, 0)
      val top = topOf(chunk, localX)
      assertEquals(BlockType.WATER, BlockType.of(chunk.blocks[offset + top].toInt() and 0xFF),
        "column x=$localX, ground at $ground under water at $WATER, is not topped with water")
      assertEquals(WATER, top + (chunk.occupancy[offset + top].toInt() and 0xFF) / 255.0, 1.0 / 255.0,
        "column x=$localX does not hold its water up to the waterline")
    }

    assertTrue(submerged >= 6, "the ramp put only $submerged columns under water; the test proves nothing")
  }

  @Test
  fun `a bed is lowered by less than a voxel to make room for its water`() {
    val chunk = materializer().materialize(ChunkPos(0, 0, 0))

    for (localX in 0 until CHUNK_SIZE) {
      val ground = groundAt(localX)
      if (ground >= WATER) continue

      val offset = chunk.columnOffset(localX, 0)
      val bedTop = (topOf(chunk, localX) downTo 0).first {
        BlockType.of(chunk.blocks[offset + it].toInt() and 0xFF) != BlockType.WATER
      } + 1

      assertTrue(bedTop <= ground + 0.5 && bedTop > ground - 1.0,
        "column x=$localX: ground at $ground came out with its bed at $bedTop")
    }
  }

  @Test
  fun `dry ground beside the water is untouched`() {
    val chunk = materializer().materialize(ChunkPos(0, 0, 0))

    for (localX in 0 until CHUNK_SIZE) {
      val ground = groundAt(localX)
      if (ground < WATER) continue

      val offset = chunk.columnOffset(localX, 0)
      val top = topOf(chunk, localX)
      assertTrue(BlockType.of(chunk.blocks[offset + top].toInt() and 0xFF) != BlockType.WATER,
        "dry column x=$localX, ground at $ground, came out wet")
      assertEquals(ground, top + (chunk.occupancy[offset + top].toInt() and 0xFF) / 255.0, 1.0 / 255.0,
        "dry column x=$localX moved its surface")
    }
  }

  private fun groundAt(localX: Int): Double = START + localX * SLOPE

  /** The index of the topmost non-air voxel of a column. */
  private fun topOf(chunk: VoxelChunk, localX: Int): Int {
    val offset = chunk.columnOffset(localX, 0)
    return (chunk.height - 1 downTo 0).first { (chunk.blocks[offset + it].toInt() and 0xFF) != 0 }
  }

  /** A gentle ramp in x through a flat water level, every column a different fraction of a voxel deep. */
  private fun materializer(): ChunkMaterializer {
    val cells = region.cellCount.toInt()

    return ChunkMaterializer(
      config = config,
      columns = ChunkColumnSource { chunk, halo ->
        ColumnHeights.build(chunk, config.chunkSize, halo) { localX, _ ->
          val worldX = (chunk.x.toLong() * config.chunkSize + localX) * config.voxelSize
          START + worldX * SLOPE
        }
      },
      strata = Stratigraphy(
        coarseElevation = FloatLayer(LayerId.ELEVATION, region, FloatArray(cells) { START.toFloat() }),
        hardness = FloatLayer(LayerId.ROCK_HARDNESS, region, FloatArray(cells) { 0.5f }),
        plateId = IntLayer(LayerId.PLATE_ID, region, IntArray(cells)),
        seed = SEED
      ),
      surface = SurfaceSampler(
        biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
        soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1.5f }),
        waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { WATER.toFloat() }),
        lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells) { -1 }),
        // Warm, so no column freezes over and the water is water.
        temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 15f }),
        seed = SEED,
        secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
        biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
      ),
      features = FeatureStore().apply { freeze() }
    )
  }

  private companion object {
    const val SEED = 0x5EEDL
    const val CHUNK_SIZE = 16
    const val CHUNK_HEIGHT = 256

    /** The water stands on a voxel boundary, as the sea does at zero. */
    const val WATER = 60.0

    /** From 0.97 under the water to 0.63 over it, in steps of a tenth: every fraction of the top voxel. */
    const val START = 59.03
    const val SLOPE = 0.1

    val config = WorldConfig(
      seed = SEED,
      widthCells = 16,
      heightCells = 16,
      chunkSize = CHUNK_SIZE,
      chunkHeight = CHUNK_HEIGHT,
      voxelSize = 1.0
    )

    val region = CellRegion.world(16, 16, Resolution.KILOMETRE)
  }
}

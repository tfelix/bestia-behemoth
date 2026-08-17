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
 * Bare rock on steep ground: the half of the deleted `CLIFF` biome that was worth keeping.
 *
 * Built on a synthetic ramp rather than a generated world, because the property under test is *what a column
 * of a known gradient caps with*, and reading the gradient out of a generated heightfield would mean the test
 * and the code under test agreed by construction.
 *
 * The three assertions worth naming:
 *
 * - **the bed shows through where no biome names a cover.** This is the whole gain over the old single
 *   `GRAVEL`: a crag is made of the rock it is cut into, so a limestone one is white and a granite one is not,
 *   and there is no table anywhere that has to be kept in step with the stratigraphy to make that true.
 * - **an ice cliff is ice and a desert scarp is sand.** The old biome could not express this, because it had to
 *   overwrite the biome to say "steep" and so could not then ask what the steep thing was made of.
 * - **the seam.** `gradientAt` is a central difference, so an edge column that fell back to a one-sided
 *   difference would disagree with the same world column seen from the neighbouring chunk - a one-voxel stripe
 *   of wrong material down every chunk border. Neither `ChunkSeamCheck` (heights only) nor `VoxelSeamCheck`
 *   (one chunk, generated twice) would catch it.
 */
class BareRockTest {

  @Test
  fun `flat ground keeps its soil and its cap`() {
    val chunk = materializerOf(Biome.GRASSLAND, slopePerMetre = 0.0).materialize(ChunkPos(0, 0, 0))

    assertEquals(BlockType.GRASS, capOf(chunk, 8, 8), "flat grassland should cap with grass")
  }

  @Test
  fun `steep ground shows the bed where the biome names no bare cover`() {
    // Grassland has no `bareCover` entry, so the cap falls through to whatever `Stratigraphy` exposes. The
    // fixture is hard rock, so that is the basement - and the point is that it is *rock*, not GRASS and not
    // the one grey GRAVEL the CLIFF biome used for every steep cell in the world.
    val chunk = materializerOf(Biome.GRASSLAND, slopePerMetre = STEEP).materialize(ChunkPos(0, 0, 0))
    val cap = capOf(chunk, 8, 8)

    assertTrue(
      cap == BlockType.GRANITE || cap == BlockType.BASALT ||
          cap == BlockType.STONE || cap == BlockType.LIMESTONE,
      "steep grassland should expose the bed, was $cap"
    )
  }

  @Test
  fun `an ice cliff is ice and a desert scarp is sand`() {
    // The distinction the CLIFF biome could not draw: it capped both in GRAVEL, because it had overwritten the
    // biome that knew the difference.
    val ice = materializerOf(Biome.ICE_SHEET, slopePerMetre = STEEP).materialize(ChunkPos(0, 0, 0))
    val desert = materializerOf(Biome.DESERT, slopePerMetre = STEEP).materialize(ChunkPos(0, 0, 0))

    assertEquals(BlockType.ICE, capOf(ice, 8, 8))
    assertEquals(BlockType.SAND, capOf(desert, 8, 8))
  }

  @Test
  fun `the cap agrees across a chunk border`() {
    // The last column of one chunk and the first of the next are the same world column, so they must reach the
    // same verdict. They only do because `materialize` takes a halo of at least one - without it the edge
    // column would take a one-sided difference and the interior one a central difference over the same ground.
    val materializer = materializerOf(Biome.GRASSLAND, slopePerMetre = STEEP)
    val left = materializer.materialize(ChunkPos(0, 0, 0))
    val right = materializer.materialize(ChunkPos(1, 0, 0))

    // The ramp is monotone in x, so the gradient is the same everywhere on it - which means the two columns
    // either side of the border must agree, and any disagreement is the halo, not the terrain.
    for (localY in 0 until CHUNK_SIZE) {
      assertEquals(
        capOf(left, CHUNK_SIZE - 1, localY),
        capOf(right, 0, localY),
        "the columns either side of the border disagree at y=$localY"
      )
    }
  }

  /** The topmost non-air block of a column: what a player standing there is standing on. */
  private fun capOf(chunk: VoxelChunk, localX: Int, localY: Int): BlockType {
    val offset = chunk.columnOffset(localX, localY)
    for (z in chunk.height - 1 downTo 0) {
      val block = BlockType.of(chunk.blocks[offset + z].toInt() and 0xFF)
      if (block != BlockType.AIR) return block
    }
    error("column ($localX,$localY) is empty")
  }

  /**
   * A world that is one uniform biome on a plane of constant gradient.
   *
   * The ramp runs in x only, so every column in a row shares a height and the border assertion compares
   * like with like. Water is below the ground everywhere, because bare rock is a dry-land rule.
   */
  private fun materializerOf(biome: Biome, slopePerMetre: Double): ChunkMaterializer {
    val cells = region.cellCount.toInt()

    return ChunkMaterializer(
      config = config,
      columns = ChunkColumnSource { chunk, halo ->
        ColumnHeights.build(chunk, config.chunkSize, halo) { localX, _ ->
          val worldX = (chunk.x.toLong() * config.chunkSize + localX) * config.voxelSize
          GROUND + worldX * slopePerMetre
        }
      },
      strata = Stratigraphy(
        coarseElevation = FloatLayer(LayerId.ELEVATION, region, FloatArray(cells) { GROUND.toFloat() }),
        hardness = FloatLayer(LayerId.ROCK_HARDNESS, region, FloatArray(cells) { 0.98f }),
        plateId = IntLayer(LayerId.PLATE_ID, region, IntArray(cells)),
        seed = SEED
      ),
      surface = SurfaceSampler(
        biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { biome.ordinal }),
        soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1.5f }),
        waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { 0f }),
        lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells) { -1 }),
        // Well above the snow line, so a failure is about the slope rule rather than about the cold one.
        // The ice case gets its ICE from `bareCover`, not from the weather.
        temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 11f }),
        seed = SEED,
        secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
        biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
      ),
      features = FeatureStore().apply { freeze() }
    )
  }

  private companion object {
    const val SEED = 0x5EEDL
    const val VOXEL = 1.0
    const val CHUNK_SIZE = 16
    const val CHUNK_HEIGHT = 256

    const val GROUND = 60.3

    /** Comfortably past `ChunkMaterializer.BARE_ROCK_GRADIENT`, so the test is not measuring the threshold. */
    const val STEEP = 2.0

    val config = WorldConfig(
      seed = SEED,
      widthCells = 16,
      heightCells = 16,
      chunkSize = CHUNK_SIZE,
      chunkHeight = CHUNK_HEIGHT,
      voxelSize = VOXEL
    )

    val region = CellRegion.world(16, 16, Resolution.KILOMETRE)
  }
}

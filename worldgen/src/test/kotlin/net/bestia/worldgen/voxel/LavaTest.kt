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
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.karst.CaveChannels
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A crater lake, materialised: the fluid, the floor under it, and the two rules that keep it out of the way of
 * everything else in a column.
 *
 * On a synthetic world for [BareRockTest]'s reason. The properties under test are what a column *inside a pool*
 * comes out as, and finding a volcano in a generated world would tie the fixture to the tectonics of one seed.
 *
 * The assertions worth naming, in the order they would be missed:
 *
 * - **lava and water are never in the same column.** The invariant the exclusive-fluid rule exists to hold, and
 *   its failure is not a crash: it is two materials under one air interface, which is the single assumption the
 *   bulk occupancy fill is built on. The fixture puts the water raster *above* the pool deliberately, so on a
 *   highest-wins rule the water would take those columns and the test would pass without the rule.
 * - **the floor is basalt and there is no soil under it.** A pool with a grass floor is what falls out of
 *   dropping the cap override, and it is invisible from anywhere except inside the crater.
 * - **nothing opens a hole under a pool.** The drain-the-lake veto standing water has always had, now reachable
 *   by a second fluid. Tested on a *dry* world so that the water cannot be the thing vetoing it - which is what
 *   makes this an assertion about `flooded` rather than about `waterDepth`.
 * - **the shoreline is a function of world position alone.** Checked against `AreaFeature.contains` itself
 *   across two chunks, rather than by comparing two adjacent columns: adjacent columns near a curved boundary
 *   legitimately differ, so an equality between them would be either vacuous or flaky.
 */
class LavaTest {

  @Test
  fun `a pool is lava over a basalt floor`() {
    val chunk = dryWorld().materialize(ChunkPos(0, 0, 0))
    val offset = chunk.columnOffset(INSIDE_X, INSIDE_Y)

    assertEquals(BlockType.LAVA, capOf(chunk, INSIDE_X, INSIDE_Y), "the top of a pool column should be lava")

    // Downwards from the surface: some lava, then the floor. The floor is the first non-lava block, and it is
    // basalt rather than the biome's grass cap or whichever bed the stratigraphy exposes.
    assertEquals(
      BlockType.BASALT, firstBelow(chunk, offset) { it != BlockType.LAVA },
      "a crater floor is chilled basalt"
    )

    // And no soil anywhere in the column: molten rock does not stand on turf. The fixture gives grassland
    // 1.5 m of it, and the column just outside the pool proves the fixture really does have soil to lose.
    assertFalse(
      holds(chunk, offset, BlockType.DIRT),
      "a pool column should carry no soil"
    )
    assertTrue(
      holds(chunk, chunk.columnOffset(OUTSIDE_X, INSIDE_Y), BlockType.DIRT),
      "the fixture should put soil on ordinary ground, or the assertion above proves nothing"
    )
  }

  @Test
  fun `a column the water raster also claims gets lava and no water at all`() {
    // The water level here is above the pool surface, so a fourth-surface highest-wins rule would make this
    // column a lake. The assertion is about the *whole* column rather than about its top: a water voxel over
    // the lava would sit a metre or two up, where nothing looking at the surface would see it.
    val chunk = floodedWorld().materialize(ChunkPos(0, 0, 0))
    val offset = chunk.columnOffset(INSIDE_X, INSIDE_Y)

    var sawLava = false
    for (z in 0 until chunk.height) {
      val block = BlockType.of(chunk.blocks[offset + z].toInt() and 0xFF)
      if (block == BlockType.LAVA) sawLava = true
      assertTrue(
        block != BlockType.WATER && block != BlockType.ICE,
        "a lava column must hold no water and no ice, found $block at z=$z"
      )
    }

    assertTrue(sawLava, "the fixture column should be inside the pool")

    // The other half of the exclusion, and the guard against writing it as "lava wherever a pool feature is in
    // range at all": a column outside the ring is still a lake.
    assertEquals(
      BlockType.WATER, capOf(chunk, OUTSIDE_X, INSIDE_Y),
      "a column outside the pool should still be under the water raster"
    )
  }

  @Test
  fun `nothing opens a hole under a pool`() {
    // A passage under a lava lake drains it exactly as one under a water lake does, and nothing anywhere would
    // refill it. On a dry world the pool is the *only* fluid, so this fails if the veto still reads `waterDepth`
    // - and the column just outside the pool is what proves the passage was there to be vetoed.
    val chunk = dryWorld(passage = true).materialize(ChunkPos(0, 0, 0))

    val inside = chunk.columnOffset(INSIDE_X, INSIDE_Y)
    for (z in 0..highestNonAir(chunk, inside)) {
      assertTrue(
        BlockType.of(chunk.blocks[inside + z].toInt() and 0xFF) != BlockType.AIR,
        "found air at z=$z under a lava pool"
      )
    }

    val outside = chunk.columnOffset(OUTSIDE_X, INSIDE_Y)
    assertTrue(
      holds(chunk, outside, BlockType.AIR, below = PASSAGE_ROOF.toInt()),
      "the fixture passage should carve the column outside the pool, or the veto proves nothing"
    )
  }

  @Test
  fun `the shoreline is decided by world position and nothing else`() {
    // The pool straddles the border between chunk 0 and chunk 1, so both materialise columns close enough to it
    // that a shoreline computed per chunk could disagree. Checked against the ring's own exact integer
    // `contains` rather than by comparing two adjacent columns, which near a curved boundary legitimately differ.
    val materializer = dryWorld()
    var lava = 0

    for (chunkX in 0..1) {
      val chunk = materializer.materialize(ChunkPos(chunkX, 0, 0))
      for (localY in 0 until CHUNK_SIZE) {
        for (localX in 0 until CHUNK_SIZE) {
          val (worldX, worldY) = config.columnCenter(chunk.chunk, localX, localY)
          val isLava = capOf(chunk, localX, localY) == BlockType.LAVA
          assertEquals(
            POOL.contains(worldX, worldY), isLava,
            "chunk $chunkX column ($localX,$localY) at ($worldX,$worldY) disagrees with the ring"
          )
          if (isLava) lava++
        }
      }
    }

    // Otherwise the loop above is satisfied by there being no pool at all.
    assertTrue(lava > 20, "the fixture pool should cover a good many columns, covered $lava")
  }

  @Test
  fun `a world with no pool materialises as it always did`() {
    // The whole of the no-lava case in two assertions: the generalisation to one fluid moved neither the lake
    // nor the dry ground beside it.
    assertEquals(BlockType.WATER, capOf(floodedWorld(pool = false).materialize(ChunkPos(0, 0, 0)), 4, 4))
    assertEquals(BlockType.GRASS, capOf(dryWorld(pool = false).materialize(ChunkPos(0, 0, 0)), 4, 4))
  }

  /** The topmost non-air block of a column. */
  private fun capOf(chunk: VoxelChunk, localX: Int, localY: Int): BlockType {
    val offset = chunk.columnOffset(localX, localY)
    return BlockType.of(chunk.blocks[offset + highestNonAir(chunk, offset)].toInt() and 0xFF)
  }

  private fun highestNonAir(chunk: VoxelChunk, offset: Int): Int {
    for (z in chunk.height - 1 downTo 0) {
      if (BlockType.of(chunk.blocks[offset + z].toInt() and 0xFF) != BlockType.AIR) return z
    }
    error("empty column")
  }

  /** The first block at or below the air interface that satisfies [predicate], scanning down. */
  private fun firstBelow(chunk: VoxelChunk, offset: Int, predicate: (BlockType) -> Boolean): BlockType {
    for (z in highestNonAir(chunk, offset) downTo 0) {
      val block = BlockType.of(chunk.blocks[offset + z].toInt() and 0xFF)
      if (predicate(block)) return block
    }
    error("nothing below the interface matched")
  }

  /** Whether a column holds [block] anywhere below the voxel index [below]. */
  private fun holds(chunk: VoxelChunk, offset: Int, block: BlockType, below: Int = CHUNK_HEIGHT): Boolean {
    for (z in 0 until minOf(below, chunk.height)) {
      if (BlockType.of(chunk.blocks[offset + z].toInt() and 0xFF) == block) return true
    }
    return false
  }

  /**
   * Flat grassland with the water table below the ground, so the pool is the only fluid in the world.
   *
   * The fixture the carve veto needs: with water anywhere over the column, the veto would fire on the water and
   * a broken lava rule would look exactly the same.
   */
  private fun dryWorld(pool: Boolean = true, passage: Boolean = false) =
    materializerOf(waterLevel = 0.0, pool = pool, passage = passage)

  /**
   * Flat grassland under four metres of standing water, above the pool's own surface.
   *
   * The fixture the exclusion needs, and the level is above [POOL_SURFACE] on purpose: with the pool standing
   * proud of the water there is no contest to lose.
   */
  private fun floodedWorld(pool: Boolean = true) =
    materializerOf(waterLevel = 64.0, pool = pool, passage = false)

  private fun materializerOf(waterLevel: Double, pool: Boolean, passage: Boolean): ChunkMaterializer {
    val cells = region.cellCount.toInt()

    return ChunkMaterializer(
      config = config,
      columns = ChunkColumnSource { chunk, halo ->
        ColumnHeights.build(chunk, config.chunkSize, halo) { _, _ -> GROUND }
      },
      strata = Stratigraphy(
        coarseElevation = FloatLayer(LayerId.ELEVATION, region, FloatArray(cells) { GROUND.toFloat() }),
        hardness = FloatLayer(LayerId.ROCK_HARDNESS, region, FloatArray(cells) { 0.98f }),
        plateId = IntLayer(LayerId.PLATE_ID, region, IntArray(cells)),
        seed = SEED
      ),
      surface = SurfaceSampler(
        biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
        soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1.5f }),
        waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { waterLevel.toFloat() }),
        lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells) { -1 }),
        // Well above freezing, so the ice branch is not what any of these assertions is measuring.
        temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 11f }),
        seed = SEED,
        secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
        biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
      ),
      features = FeatureStore().apply {
        if (pool) add(StageId("volcanism"), listOf(POOL))
        if (passage) add(StageId("caves"), listOf(passageFeature()))
        freeze()
      }
    )
  }

  /** A gallery in the rock well under the pool, running the length of both chunks. */
  private fun passageFeature(): MarkerFeature {
    val line = Polyline(listOf(Vec2d(0.0, POOL_CENTRE_Y), Vec2d(CHUNK_SIZE * 2.0 * VOXEL, POOL_CENTRE_Y)))

    return MarkerFeature(
      id = FeatureId(2L),
      kind = FeatureKind.CAVE_PASSAGE,
      centerline = line,
      stations = StationTable.Builder(line.vertexCount)
        .channel(CaveChannels.FLOOR) { PASSAGE_FLOOR }
        .channel(CaveChannels.HEIGHT) { PASSAGE_ROOF - PASSAGE_FLOOR }
        .channel(CaveChannels.HALF_WIDTH) { 4.0 }
        .build()
    )
  }

  private companion object {
    const val SEED = 0x5EEDL
    const val VOXEL = 1.0
    const val CHUNK_SIZE = 16
    const val CHUNK_HEIGHT = 256

    const val GROUND = 60.3

    /** Above the ground, so the pool fills its crater; below [floodedWorld]'s water table, so they compete. */
    const val POOL_SURFACE = 62.5

    /** On the border between chunk 0 and chunk 1, and wide enough to cross it. */
    const val POOL_CENTRE_X = CHUNK_SIZE * VOXEL
    const val POOL_CENTRE_Y = CHUNK_SIZE / 2 * VOXEL
    const val POOL_RADIUS = 9.0

    /** Deep in the rock, so the passage is nowhere near the pool it must not drain. */
    const val PASSAGE_FLOOR = 40.0
    const val PASSAGE_ROOF = 46.0

    /** A column comfortably inside the pool, and one comfortably outside it, in chunk 0. */
    const val INSIDE_X = CHUNK_SIZE - 2
    const val INSIDE_Y = CHUNK_SIZE / 2
    const val OUTSIDE_X = 2

    val config = WorldConfig(
      seed = SEED,
      widthCells = 16,
      heightCells = 16,
      chunkSize = CHUNK_SIZE,
      chunkHeight = CHUNK_HEIGHT,
      voxelSize = VOXEL
    )

    val region = CellRegion.world(16, 16, Resolution.KILOMETRE)

    val POOL: AreaFeature = Ring
      .warpedCircle(Vec2d(POOL_CENTRE_X, POOL_CENTRE_Y), POOL_RADIUS, seed = 7L, vertexCount = 24)
      .let { ring ->
        AreaFeature(
          id = FeatureId(1L),
          kind = FeatureKind.LAVA_POOL,
          ring = ring,
          perimeter = StationTable.Builder(ring.vertexCount, periodic = true)
            .channel(VolcanismStage.CHANNEL_SURFACE_ELEVATION) { POOL_SURFACE }
            .build()
        )
      }
  }
}

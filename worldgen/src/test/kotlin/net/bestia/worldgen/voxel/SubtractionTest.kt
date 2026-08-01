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
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Voxel subtraction: the span buffer that can now remove, and the carve that applies it.
 *
 * Built on a flat synthetic world with one mine in it rather than on a generated one, because the properties
 * asserted here are about *which columns and which voxels* a removal touches, and that needs a world where the
 * answer is known in advance rather than read out of the thing under test.
 *
 * The two assertions worth naming, because they are the ones that fail interestingly:
 *
 * - **the shaft opens to the sky.** A void whose ceiling stops at the ground keeps the voxel the surface falls
 *   inside, and that leftover voxel is a lid. The failure looks exactly like the bug subtraction was built to
 *   fix - `probe -Pon=mine` reporting undisturbed grass over a hole - and it is one constant away at all times.
 * - **the ground beside the shaft is untouched.** One buffer is refilled per column, so a stale `count` smears
 *   the last column's spans over its neighbours. In a heightfield that is invisible; here it is the difference
 *   between a shaft and a trench.
 */
class SubtractionTest {

  // --- The buffer -----------------------------------------------------------------------------------

  @Test
  fun `a removal is a span made of air, and knows itself`() {
    val spans = StructureSpans()
    spans.add(10.0, 12.0, BlockType.MASONRY)
    spans.remove(4.0, 9.0)

    assertEquals(2, spans.count)
    assertTrue(!spans.isRemoval(0), "masonry is not a removal")
    assertTrue(spans.isRemoval(1), "air is")
    assertEquals(BlockType.AIR.id, spans.blockOf(1))
    assertEquals(4.0, spans.bottomOf(1))
    assertEquals(9.0, spans.topOf(1))
  }

  @Test
  fun `adding air is refused rather than silently meaning removal`() {
    // Air is the removal sentinel, so `add(AIR)` is ambiguous by construction: it reads as "build nothing"
    // and would act as "delete everything between these two elevations".
    val spans = StructureSpans()
    assertFailsWith<IllegalArgumentException> { spans.add(1.0, 2.0, BlockType.AIR) }
  }

  @Test
  fun `an inverted span is dropped, and is not counted as truncation`() {
    // The check that makes an inverted-span convention for removal unavailable, and deliberately so: two
    // elevations the wrong way round is a routine slip, and it must build nothing rather than dig.
    val spans = StructureSpans()
    spans.add(12.0, 10.0, BlockType.MASONRY)
    spans.remove(9.0, 4.0)

    assertEquals(0, spans.count)
    assertEquals(0, spans.dropped, "an inverted span is a caller asking for nothing, not a full buffer")
  }

  @Test
  fun `a full buffer counts what it refused`() {
    val spans = StructureSpans()
    repeat(11) { i -> spans.add(i * 2.0, i * 2.0 + 1.0, BlockType.MASONRY) }

    assertEquals(8, spans.count, "capacity")
    assertEquals(3, spans.dropped)

    // clear() runs per column, so a drop count it reset could only ever read 0 or 1 and would have to be
    // checked a quarter of a million times to mean anything.
    spans.clear()
    assertEquals(0, spans.count)
    assertEquals(3, spans.dropped, "clear() is per column and the drop count is not")
  }

  @Test
  fun `the ceiling ignores holes`() {
    // Every caller asks this to find out whether something has been built over a column. A passage's ceiling is
    // the opposite of an answer to that, and counting it would report a cave as a roof.
    val spans = StructureSpans()
    assertTrue(spans.ceiling().isNaN(), "nothing built")

    spans.remove(2.0, 40.0)
    assertTrue(spans.ceiling().isNaN(), "a hole is not a roof")

    spans.add(1.0, 3.5, BlockType.MASONRY)
    assertEquals(3.5, spans.ceiling())
  }

  // --- The carve ------------------------------------------------------------------------------------

  @Test
  fun `the mine shaft is a hole that reaches the surface`() {
    val chunk = materializeMine()
    val (localX, localY) = localOf(MINE_X, MINE_Y)

    val shaftTop = chunk.highestNonAir(localX, localY)

    assertTrue(
      shaftTop < SURFACE_VOXEL,
      "the column at the middle of the mine head still has material at or above the ground surface " +
          "(topmost non-air voxel $shaftTop, the surface is voxel $SURFACE_VOXEL) - the shaft has a lid on it"
    )
  }

  @Test
  fun `the shaft goes down as far as the marker asked and stops`() {
    val chunk = materializeMine()
    val (localX, localY) = localOf(MINE_X, MINE_Y)
    val offset = chunk.columnOffset(localX, localY)

    // The void is found by scanning rather than computed from the constants, so this asserts the shape of the
    // result and not a second copy of the arithmetic that produced it.
    var lowestAir = SURFACE_VOXEL
    while (lowestAir > 0 && chunk.blocks[offset + lowestAir - 1] == AIR) lowestAir--

    assertTrue(
      SURFACE_VOXEL - lowestAir > 10,
      "the void is ${SURFACE_VOXEL - lowestAir} voxels deep; that is a scrape, not a shaft"
    )
    assertTrue(
      BlockType.of(chunk.rawAt(localX, localY, lowestAir - 1)).solid,
      "no floor under the shaft - it is a chimney through the world"
    )

    // The floor voxel keeps its material and part of its fill, so the standable surface is where the marker put
    // it rather than rounded to a voxel boundary. That is the fill rule, and it is why occupancy exists.
    val floor = (lowestAir - 1) * VOXEL + chunk.fillAt(localX, localY, lowestAir - 1) * VOXEL
    assertEquals(GROUND - MineHead.SHAFT_DEPTH, floor, 0.01, "the shaft floor is not at the requested depth")
  }

  @Test
  fun `the ground beside the shaft keeps its rock`() {
    val chunk = materializeMine()

    // Every column of the chunk, classified by whether it is inside the shaft and checked for air below the
    // surface. A stale span buffer shows up here and nowhere else: it puts a hole in the column after the last
    // one that asked for one.
    var holesInside = 0
    var holesOutside = 0

    for (localY in 0 until chunk.size) {
      for (localX in 0 until chunk.size) {
        val offset = chunk.columnOffset(localX, localY)
        var air = 0
        for (z in 0 until SURFACE_VOXEL) {
          if (chunk.blocks[offset + z] == AIR) air++
        }
        if (air == 0) continue

        if (insideShaft(chunk.chunk, localX, localY)) holesInside++ else holesOutside++
      }
    }

    assertTrue(holesInside > 0, "no column inside the shaft has any air under the surface")
    assertEquals(0, holesOutside, "$holesOutside columns outside the shaft have air under the surface")
  }

  @Test
  fun `the chunk invariant survives being carved`() {
    // The reason no new VoxelChunk.set overload was needed: every voxel the carve writes is air at zero fill or
    // material at a strictly positive one. `validate` names the first voxel that is neither.
    materializeMine().validate()
  }

  @Test
  fun `nothing is carved under standing water`() {
    // The veto lives at the call site rather than in each producer. A shaft under a lake would drain it: the
    // water is a level raster surface, and nothing in the pipeline would lower it or fill the hole.
    val chunk = materializeMine(waterLevel = GROUND + 5.0)
    val (localX, localY) = localOf(MINE_X, MINE_Y)
    val offset = chunk.columnOffset(localX, localY)

    var air = 0
    for (z in 0 until SURFACE_VOXEL) {
      if (chunk.blocks[offset + z] == AIR) air++
    }
    assertEquals(0, air, "a shaft was opened under five metres of standing water")
  }

  @Test
  fun `two generations of a carved chunk agree byte for byte`() {
    val report = VoxelSeamCheck.run(
      materializerOf(waterLevel = Double.NaN),
      origin = ChunkPos(
        Math.floorDiv((MINE_X / VOXEL).toInt(), CHUNK_SIZE),
        Math.floorDiv((MINE_Y / VOXEL).toInt(), CHUNK_SIZE)
      ),
      blockSize = 2,
      threads = 4
    )

    assertTrue(report.isClean, report.toString())
    assertTrue(report.solidVoxels > 0, "the check ran over nothing but air: $report")
  }

  // --- The synthetic world --------------------------------------------------------------------------

  private fun materializeMine(waterLevel: Double = Double.NaN): VoxelChunk = materializerOf(waterLevel)
    .materialize(
      ChunkPos(
        Math.floorDiv((MINE_X / VOXEL).toInt(), CHUNK_SIZE),
        Math.floorDiv((MINE_Y / VOXEL).toInt(), CHUNK_SIZE),
        Math.floorDiv(SURFACE_VOXEL, CHUNK_HEIGHT)
      )
    )

  private fun materializerOf(waterLevel: Double): ChunkMaterializer {
    val cells = region.cellCount.toInt()

    return ChunkMaterializer(
      config = config,
      columns = ChunkColumnSource { chunk, halo ->
        ColumnHeights.build(chunk, config.chunkSize, halo) { _, _ -> GROUND }
      },
      strata = Stratigraphy(
        coarseElevation = FloatLayer(LayerId.ELEVATION, region, FloatArray(cells) { GROUND.toFloat() }),
        // Hard rock, so the sedimentary cover is thin and the shaft is cut through basement granite. What it
        // is cut through does not matter here; that it is one material makes a failure easier to read.
        hardness = FloatLayer(LayerId.ROCK_HARDNESS, region, FloatArray(cells) { 0.98f }),
        plateId = IntLayer(LayerId.PLATE_ID, region, IntArray(cells)),
        seed = SEED
      ),
      surface = SurfaceSampler(
        biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.GRASSLAND.ordinal }),
        soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1.5f }),
        waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { waterLevel.toFloat() }),
        lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells) { -1 }),
        temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 11f }),
        seed = SEED,
        secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
        biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
      ),
      features = FeatureStore().apply {
        add(StageId("test-history"), listOf(mineMarker()))
        freeze()
      }
    )
  }

  private fun mineMarker() = PointMarker(
    id = FeatureId(1L),
    kind = FeatureKind.MINE,
    position = Vec2d(MINE_X, MINE_Y),
    attributes = StationTable.Builder(1)
      .channel(SiteChannels.RADIUS) { MINE_RADIUS }
      .channel(SiteChannels.DECAY) { 0.0 }
      .build()
  )

  /** Local column of a world position inside the chunk [materializeMine] builds. */
  private fun localOf(worldX: Double, worldY: Double): Pair<Int, Int> = Pair(
    Math.floorMod((worldX / VOXEL).toInt(), CHUNK_SIZE),
    Math.floorMod((worldY / VOXEL).toInt(), CHUNK_SIZE)
  )

  private fun insideShaft(chunk: ChunkPos, localX: Int, localY: Int): Boolean {
    val (x, y) = config.columnCenter(chunk, localX, localY)
    return hypot(x - MINE_X, y - MINE_Y) < MINE_RADIUS * MineHead.SHAFT_SHARE
  }

  private companion object {
    const val SEED = 0x5EEDL
    const val VOXEL = 1.0
    const val CHUNK_SIZE = 16
    const val CHUNK_HEIGHT = 128

    /**
     * Three tenths of a metre off a voxel boundary, and the three tenths are load bearing.
     *
     * The shaft floor then lands at 36.3 m, which is in the *lower* half of its voxel - and that is the only
     * place the fill rule and the centre rule disagree about which voxel the floor is in. At 60.5 m both rules
     * pick the same voxel and the floor assertion cannot tell a correct carve from one that rounds the floor
     * down to the nearest metre.
     */
    const val GROUND = 60.3

    /** The voxel the ground surface falls inside - the fill rule, spelled out for the assertions. */
    const val SURFACE_VOXEL = 60

    const val MINE_X = 5_000.0
    const val MINE_Y = 5_000.0

    /** `HistorySim.MINE_RADIUS`, which is what the real markers carry. */
    const val MINE_RADIUS = 34.0

    val AIR = BlockType.AIR.id.toByte()

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

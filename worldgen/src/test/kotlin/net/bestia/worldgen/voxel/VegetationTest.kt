package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
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
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vegetation scatter: where it puts a tree, and what it refuses to put one on.
 *
 * On a synthetic world rather than a generated one, for the reason `SubtractionTest` gives: every property
 * here is about *which* columns and voxels get a tree, and that wants a world where the answer is known in
 * advance rather than read back out of the thing being tested. One biome, one plane of ground, and the only
 * variation is the one the scatter itself introduces.
 *
 * The three that matter most, because they are the ones that fail interestingly:
 *
 * - **the canopy comes in patches.** This module has drawn a 50/50 checkerboard once already, out of a
 *   per-position hash of a smooth probability, and every test passed while it did. The measurement here is
 *   run length along a scanline and the control is that same broken construction rebuilt inside the test, so
 *   the assertion is "better than the bug" rather than "above a number somebody liked".
 * - **a crown is level.** Hanging a crown off the ground under the *column* rather than the ground under its
 *   own *trunk* is a one-word change, is invisible on flat ground, costs a chunk halo to avoid, and drapes
 *   every tree in the world over the hillside it stands on. The fixture is therefore a slope, and the
 *   assertion is on the crown's own centre elevation, exactly, with no tolerance to hide in.
 * - **a tree grows out of the ground, not into it.** The whole of `onlyIntoAir`, and exercised by every tree
 *   there is: a trunk's first voxel by the span writer's rounding *is* the surface voxel, so without the flag
 *   every tree in the world replaces the patch of grass it stands on.
 */
class VegetationTest {

  // --- The lattice ----------------------------------------------------------------------------------

  @Test
  fun `a canopy comes in patches, not in speckle`() {
    val strip = canopyStrip()
    val share = strip.count { it }.toDouble() / strip.size

    // Habit 5: an invariant that skips its subject reports success. A strip with no trees on it has a
    // magnificent run length of nothing.
    assertTrue(share > 0.15, "only $share of the strip is under canopy; there is nothing here to measure")

    val measured = meanRunLength(strip)

    // The control: the same share of ground decided per column by a hash, which is precisely the construction
    // `SurfaceSampler.biomeAt` was measured and rejected for. Its runs are geometric with mean `1/(1-p)`, so
    // at any plausible share it sits near 1.5 and no amount of tuning the probability rescues it.
    val speckled = BooleanArray(strip.size) { GenRng.hashUnit(SEED, CONTROL_SALT, it.toLong()) < share }
    val control = meanRunLength(speckled)

    assertTrue(
      measured >= MIN_RUN_METRES,
      "canopy runs average $measured m, under the $MIN_RUN_METRES m a single crown is wide - this is speckle"
    )
    assertTrue(
      measured > control * RUN_MARGIN,
      "canopy runs average $measured m against $control m for a per-column coin flip at the same share, " +
          "which is not a distinction"
    )
  }

  @Test
  fun `a crown hangs from its own trunk, not from the ground under each column`() {
    val scatter = scatterOf()
    val spans = StructureSpans()
    var checked = 0

    for (offsetY in 0 until FOREST_BLOCK) {
      for (offsetX in 0 until FOREST_BLOCK) {
        val chunk = ChunkPos(FOREST_CHUNK + offsetX, FOREST_CHUNK + offsetY)
        val bounds = config.chunkBounds(chunk)
        val candidates = scatter.candidatesIn(chunk)
        val lattice = scatter.plant(candidates) { worldX, _ -> groundAt(worldX) }

        for (i in 0 until candidates.cellsX * candidates.cellsY) {
          val trunkX = candidates.trunkX[i]
          if (trunkX.isNaN()) continue
          val trunkY = candidates.trunkY[i]
          val radius = candidates.canopyRadius[i]

          // Only trees whose whole crown is inside this chunk. One in the surrounding ring has half of its
          // crown over cells this lattice does not cover, and asking about those columns would be testing the
          // fixture rather than the scatter.
          if (!bounds.expanded(-radius).contains(trunkX, trunkY)) continue

          // The crown's centre elevation, computed the one way it is allowed to be: the ground under the
          // trunk, plus the trunk. Every column the crown reaches must produce a leaf span centred on
          // exactly this.
          val centre = groundAt(trunkX) + candidates.trunkHeight[i]

          for (offset in listOf(-radius * 0.5, 0.0, radius * 0.5)) {
            val worldX = trunkX + offset
            // Sloped east to west, so an offset along y would not tell a level crown from a draped one.
            if (offset != 0.0) {
              assertTrue(
                groundAt(worldX) != groundAt(trunkX),
                "the fixture is flat here, so this proves nothing"
              )
            }

            spans.clear()
            lattice.columnAt(worldX, trunkY, spans)

            var found = false
            for (span in 0 until spans.count) {
              if (spans.blockOf(span) != BlockType.LEAVES.id) continue
              if ((spans.bottomOf(span) + spans.topOf(span)) * 0.5 == centre) found = true
            }

            assertTrue(
              found,
              "the column $offset m from the trunk at ($trunkX,$trunkY) holds no leaf span centred on " +
                  "$centre; the ground under it is ${groundAt(worldX)}, which is what a draped crown " +
                  "would follow"
            )
            checked++
          }
        }
      }
    }

    assertTrue(checked > 100, "only $checked columns were testable")
  }

  @Test
  fun `two chunks draw the same tree on the border between them`() {
    // The seam property, asked of the trees rather than of the voxels: a crown straddling a chunk border is
    // drawn half by each side, so both have to agree about where its trunk is and how big it is. They share
    // no state and never see each other, so the only thing making them agree is that every draw comes off an
    // integer lattice index of a quantised world coordinate.
    val scatter = scatterOf()
    val left = scatter.candidatesIn(ChunkPos(0, 0))
    val right = scatter.candidatesIn(ChunkPos(1, 0))

    var shared = 0
    for (cellY in right.fromCellY until right.fromCellY + right.cellsY) {
      for (cellX in right.fromCellX until right.fromCellX + right.cellsX) {
        val a = left.indexOf(cellX, cellY)
        val b = right.indexOf(cellX, cellY)
        if (a < 0 || b < 0) continue
        // A tree too far outside a chunk to reach it is dropped by that chunk alone, which is not a
        // disagreement - it is one side declining to draw something it cannot see.
        if (left.trunkX[a].isNaN() || right.trunkX[b].isNaN()) continue

        assertEquals(left.trunkX[a], right.trunkX[b], "trunk x at cell ($cellX,$cellY)")
        assertEquals(left.trunkY[a], right.trunkY[b], "trunk y at cell ($cellX,$cellY)")
        assertEquals(left.trunkHeight[a], right.trunkHeight[b], "trunk height at cell ($cellX,$cellY)")
        assertEquals(left.canopyRadius[a], right.canopyRadius[b], "canopy radius at cell ($cellX,$cellY)")
        shared++
      }
    }

    assertTrue(shared > 0, "the two chunks share no tree at all, so this asserted nothing")
  }

  @Test
  fun `the ground a crown hangs from is the same column height either chunk reads`() {
    // The other half of the seam, and the one that costs something: a crown's elevation comes from the ground
    // under its trunk, which for a tree just outside a chunk is a column belonging to the neighbour. It is
    // read out of a halo on the height source rather than resampled, and this is what makes that sound.
    val columns = slopedColumns()
    val halo = scatterOf().halo
    assertTrue(halo > 0, "a halo of zero cannot reach a neighbour's column")

    val left = columns.heights(ChunkPos(0, 0), halo)
    val right = columns.heights(ChunkPos(1, 0), halo)

    for (localY in 0 until CHUNK_SIZE) {
      for (offset in 0 until halo) {
        assertEquals(
          left[CHUNK_SIZE + offset, localY],
          right[offset, localY],
          "chunk 0's halo column ${CHUNK_SIZE + offset} is chunk 1's own column $offset"
        )
      }
    }
  }

  // --- The voxels -----------------------------------------------------------------------------------

  @Test
  fun `a tree grows out of the ground and never into it`() {
    // `onlyIntoAir`, exercised by every tree in the world rather than by a contrived one: a trunk starts at
    // its own ground elevation, and by the span writer's rounding the first voxel that reaches is the surface
    // voxel itself. Without the flag every tree replaces the patch of grass it stands on with wood.
    var trunks = 0

    for (chunk in forestChunks) {
      val baseZ = config.voxelBaseOf(chunk.chunk)

      for (localY in 0 until CHUNK_SIZE) {
        for (localX in 0 until CHUNK_SIZE) {
          val (worldX, _) = config.columnCenter(chunk.chunk, localX, localY)
          // The voxel the ground surface falls inside - `ChunkMaterializer.topFilledVoxel`, spelled out.
          val surface = ceil(groundAt(worldX) / VOXEL).toInt() - 1 - baseZ
          if (surface !in 0 until CHUNK_HEIGHT) continue

          for (localZ in 0..surface) {
            val block = chunk.rawAt(localX, localY, localZ)
            assertTrue(
              block != BlockType.LOG.id && block != BlockType.LEAVES.id,
              "${BlockType.of(block)} at ($localX,$localY,$localZ) of ${chunk.chunk}, at or below the " +
                  "ground surface"
            )
          }

          for (localZ in surface + 1 until CHUNK_HEIGHT) {
            if (chunk.rawAt(localX, localY, localZ) == BlockType.LOG.id) {
              trunks++
              break
            }
          }
        }
      }
    }

    assertTrue(trunks > 10, "only $trunks trunks stood in the block, which asserts nothing")
  }

  @Test
  fun `a leaf voxel is a whole voxel`() {
    // Occupancy exists to recover a continuous surface, and a canopy has none: a fractional top leaf is not
    // half a leaf, it is a surface net told to draw a smooth green dome over the wood. See
    // `ChunkMaterializer.writeStructure`'s `wholeVoxels`.
    var leaves = 0

    for (chunk in forestChunks) {
      for (localY in 0 until CHUNK_SIZE) {
        for (localX in 0 until CHUNK_SIZE) {
          for (localZ in 0 until CHUNK_HEIGHT) {
            if (chunk.rawAt(localX, localY, localZ) != BlockType.LEAVES.id) continue
            leaves++
            assertEquals(
              Occupancy.FULL,
              chunk.occupancyAt(localX, localY, localZ),
              "the leaf at ($localX,$localY,$localZ) of ${chunk.chunk} is only partly there"
            )
          }
        }
      }
    }

    assertTrue(leaves > 100, "only $leaves leaf voxels in the block, which asserts nothing")
  }

  @Test
  fun `nothing is planted on worked ground`() {
    // The veto is applied at the *trunk*, never at the column being filled, so that the chunk next door
    // drawing the other half of a crown reaches the same verdict about the same tree. What that buys here is
    // the simplest possible assertion: the top of the mine collar is masonry, all of it.
    val chunk = materializeMine()
    var collar = 0

    for (localY in 0 until CHUNK_SIZE) {
      for (localX in 0 until CHUNK_SIZE) {
        val (worldX, worldY) = config.columnCenter(chunk.chunk, localX, localY)
        val distance = hypot(worldX - MINE_X, worldY - MINE_Y)
        if (distance >= MINE_RADIUS * MineHead.COLLAR_SHARE) continue
        if (distance < MINE_RADIUS * MineHead.SHAFT_SHARE) continue
        collar++

        val solid = chunk.highestSolid(localX, localY)
        assertTrue(solid >= 0, "the collar column ($localX,$localY) holds nothing solid at all")
        assertEquals(
          BlockType.MASONRY.id,
          chunk.rawAt(localX, localY, solid),
          "the top of the mine collar at ($localX,$localY) is not masonry"
        )
      }
    }

    assertTrue(collar > 10, "only $collar columns of collar were tested")
  }

  @Test
  fun `nothing is planted in standing water`() {
    // Three metres over the highest ground in the chunk, not thirty. Deep water fills the whole slab, and a
    // column with no air in it has nothing to plant a tree into - so the first version of this passed with the
    // veto deleted, which is habit 5 exactly: an assertion whose subject was never reached. Shallow water
    // leaves twenty voxels of sky, and a crown would stand in it.
    val drowned = materializerOf(waterLevel = groundAt(FOREST_CHUNK_EAST_EDGE) + 3.0)
      .materialize(forestChunkAt(0, 0))

    for (localY in 0 until CHUNK_SIZE) {
      for (localX in 0 until CHUNK_SIZE) {
        for (localZ in 0 until CHUNK_HEIGHT) {
          val block = drowned.rawAt(localX, localY, localZ)
          assertTrue(
            block != BlockType.LOG.id && block != BlockType.LEAVES.id,
            "a tree at ($localX,$localY,$localZ) under ten metres of water"
          )
        }
      }
    }
  }

  // --- Helpers --------------------------------------------------------------------------------------

  /** Whether each metre along a west-east strip is under a canopy. */
  private fun canopyStrip(): BooleanArray {
    val scatter = scatterOf()
    val spans = StructureSpans()
    val strip = BooleanArray(STRIP_CHUNKS * CHUNK_SIZE)

    for (chunkX in 0 until STRIP_CHUNKS) {
      val chunk = ChunkPos(chunkX, 0)
      val lattice = scatter.plant(scatter.candidatesIn(chunk)) { worldX, _ -> groundAt(worldX) }

      for (localX in 0 until CHUNK_SIZE) {
        val (worldX, worldY) = config.columnCenter(chunk, localX, 0)
        spans.clear()
        lattice.columnAt(worldX, worldY, spans)
        strip[chunkX * CHUNK_SIZE + localX] = spans.count > 0
      }
    }

    return strip
  }

  /** Mean length of a run of `true`, in samples. */
  private fun meanRunLength(strip: BooleanArray): Double {
    var runs = 0
    var total = 0
    var previous = false

    for (value in strip) {
      if (value) {
        total++
        if (!previous) runs++
      }
      previous = value
    }

    return if (runs == 0) 0.0 else total.toDouble() / runs
  }

  /** Ground rising half a metre per metre eastward, so a draped crown is wrong by half a metre per metre. */
  private fun groundAt(worldX: Double) = GROUND_AT_ORIGIN + SLOPE * worldX

  private fun slopedColumns() = ChunkColumnSource { chunk, halo ->
    ColumnHeights.build(chunk, config.chunkSize, halo) { localX, localY ->
      groundAt(config.columnCenter(chunk, localX, localY).first)
    }
  }

  private fun scatterOf() = VegetationScatter(config, surfaceOf(Double.NaN), SEED)

  private fun materializeMine(): VoxelChunk = materializerOf(Double.NaN).materialize(
    ChunkPos(
      Math.floorDiv((MINE_X / VOXEL).toInt(), CHUNK_SIZE),
      Math.floorDiv((MINE_Y / VOXEL).toInt(), CHUNK_SIZE),
      Math.floorDiv((groundAt(MINE_X) / VOXEL).toInt(), CHUNK_HEIGHT)
    )
  )

  private fun forestChunkAt(offsetX: Int, offsetY: Int): ChunkPos {
    val chunkX = FOREST_CHUNK + offsetX
    val chunkY = FOREST_CHUNK + offsetY
    return ChunkPos(
      chunkX, chunkY,
      Math.floorDiv((groundAt(chunkX * CHUNK_SIZE * VOXEL) / VOXEL).toInt(), CHUNK_HEIGHT)
    )
  }

  /**
   * A block of chunks of forest, far enough from the mine that nothing built reaches them.
   *
   * A block rather than one chunk, and the reason is worth keeping: the patch field is a hundred and forty
   * metres across and a chunk here is sixteen, so a single chunk lands wholly inside a clearing often enough
   * that a one-chunk fixture is a coin flip. The first draft of this file was exactly that, and reported
   * "0 leaf voxels" - habit 5 in miniature, an assertion with nothing to assert on.
   */
  private val forestChunks: List<VoxelChunk> by lazy {
    val materializer = materializerOf(Double.NaN)
    (0 until FOREST_BLOCK).flatMap { y ->
      (0 until FOREST_BLOCK).map { x -> materializer.materialize(forestChunkAt(x, y)) }
    }
  }

  private fun surfaceOf(waterLevel: Double): SurfaceSampler {
    val cells = region.cellCount.toInt()
    return SurfaceSampler(
      // A closed forest, so that there are trees to measure at all. Grassland would be a legitimate world and
      // a useless fixture.
      biome = IntLayer(LayerId.BIOME, region, IntArray(cells) { Biome.TEMPERATE_FOREST.ordinal }),
      soilDepth = FloatLayer(LayerId.SOIL_DEPTH, region, FloatArray(cells) { 1.5f }),
      waterLevel = FloatLayer(LayerId.WATER_LEVEL, region, FloatArray(cells) { waterLevel.toFloat() }),
      lakeId = IntLayer(LayerId.LAKE_ID, region, IntArray(cells) { -1 }),
      temperature = FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { 11f }),
      seed = SEED,
      secondaryBiome = IntLayer(LayerId.BIOME_SECONDARY, region, IntArray(cells) { LayerId.NO_SECONDARY }),
      biomeConfidence = FloatLayer(LayerId.BIOME_CONFIDENCE, region, FloatArray(cells) { 1f })
    )
  }

  private fun materializerOf(waterLevel: Double): ChunkMaterializer {
    val cells = region.cellCount.toInt()

    return ChunkMaterializer(
      config = config,
      columns = slopedColumns(),
      strata = Stratigraphy(
        coarseElevation = FloatLayer(LayerId.ELEVATION, region, FloatArray(cells) { GROUND_AT_ORIGIN.toFloat() }),
        hardness = FloatLayer(LayerId.ROCK_HARDNESS, region, FloatArray(cells) { 0.98f }),
        plateId = IntLayer(LayerId.PLATE_ID, region, IntArray(cells)),
        seed = SEED
      ),
      surface = surfaceOf(waterLevel),
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

  private companion object {
    const val SEED = 0x1EAFL
    const val VOXEL = 1.0
    const val CHUNK_SIZE = 16
    const val CHUNK_HEIGHT = 128

    /** Three tenths off a voxel boundary, so no rounding rule is ever exercised at its tie. */
    const val GROUND_AT_ORIGIN = 40.3

    /**
     * Metres of rise per metre east.
     *
     * Steep on purpose. A draped crown is wrong by `slope * distance from the trunk`, so a gentle slope
     * would let the level-crown test pass against the bug it exists to catch.
     */
    const val SLOPE = 0.5

    const val MINE_X = 3_000.0
    const val MINE_Y = 3_000.0
    const val MINE_RADIUS = 34.0

    /** Well clear of the mine: 6 km against `ChunkMaterializer.MARKER_MARGIN`'s 320 m. */
    const val FOREST_CHUNK = 375

    /** Chunks per side of the forest block. Six squared is a hundred metres, most of a patch wavelength. */
    const val FOREST_BLOCK = 6

    /** East edge of the first chunk of the block, which is where its ground is highest. */
    const val FOREST_CHUNK_EAST_EDGE = (FOREST_CHUNK + 1) * CHUNK_SIZE * VOXEL

    /** Long enough for a few dozen crowns and the gaps between them. */
    const val STRIP_CHUNKS = 24

    /**
     * Metres a canopy run must average.
     *
     * Under the narrowest crown the scatter can draw, so this is a bound on "did the decision unit survive"
     * rather than a pin on the tuning.
     */
    const val MIN_RUN_METRES = 3.0

    /** How much longer than a coin flip's runs the real ones have to be. */
    const val RUN_MARGIN = 1.6

    const val CONTROL_SALT = 0x5EC70BEEFL

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

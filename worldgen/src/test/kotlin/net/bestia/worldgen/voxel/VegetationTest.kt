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

  /**
   * The canopy comes in patches rather than speckle.
   *
   * ### Retargeted, and the original measurement is gone rather than weakened
   *
   * This used to walk a scanline of *voxel columns* and measure the run length of leaf cover against a control
   * built from the broken construction the module drew a checkerboard with once. A prop is a point, not a
   * three-metre disc, so "run length of canopy along a scanline" has no subject any more - and rewriting it to
   * measure runs of *props* would report the lattice spacing rather than the patch field.
   *
   * So the property is asserted where it actually lives: the run length of `densityAt > 0`, which is the field
   * that decides where a wood is, against the same per-sample coin flip at the same share. That control is the
   * point of the test - the assertion is "better than the bug", not "above a number somebody liked".
   *
   * The clumping half of what the old test covered is now
   * [nearest-neighbour spacing is spread rather than on a pitch], which measures it directly on the props.
   */
  @Test
  fun `a wood has an edge rather than thinning to speckle everywhere`() {
    val scatter = scatterOf()
    val samples = STRIP_CHUNKS * CHUNK_SIZE
    val worldY = FOREST_CHUNK * CHUNK_SIZE * VOXEL + 0.5

    val wooded = BooleanArray(samples) { i ->
      scatter.densityAt(FOREST_CHUNK * CHUNK_SIZE * VOXEL + i * VOXEL, worldY) > 0.0
    }
    val share = wooded.count { it }.toDouble() / wooded.size

    // Habit 5: an invariant that skips its subject reports success.
    assertTrue(share > 0.15, "only $share of the strip is wooded at all; there is nothing here to measure")

    val measured = meanRunLength(wooded)

    // The control: the same share decided per sample by a hash, which is precisely the construction
    // `SurfaceSampler.biomeAt` was measured and rejected for. Its runs are geometric with mean `1/(1-p)`, so
    // at any plausible share it sits near 1.5 and no amount of tuning the probability rescues it.
    val speckled = BooleanArray(samples) { GenRng.hashUnit(SEED, CONTROL_SALT, it.toLong()) < share }
    val control = meanRunLength(speckled)

    assertTrue(
      measured >= MIN_RUN_METRES,
      "wooded runs average $measured m, under the $MIN_RUN_METRES m a single crown is wide - this is speckle"
    )
    assertTrue(
      measured > control * RUN_MARGIN,
      "wooded runs average $measured m against $control m for a per-sample coin flip at the same share, " +
          "which is not a distinction"
    )
  }

  // --- The prop lattice -----------------------------------------------------------------------------

  /**
   * The mean has to be one or a `VEGETATION_STAND` advertises a capacity nothing will fill, and both
   * sides of that disagreement would still look plausible. Measured rather than argued from the symmetry
   * of gradient noise.
   */
  @Test
  fun `the clump field averages one`() {
    val scatter = scatterOf()
    var sum = 0.0
    var samples = 0

    // A stride that is not a multiple of the wavelength, over enough area for a few hundred cycles.
    var y = 0.0
    while (y < CLUMP_SAMPLE_SPAN) {
      var x = 0.0
      while (x < CLUMP_SAMPLE_SPAN) {
        sum += scatter.clumpAt(x, y)
        samples++
        x += CLUMP_SAMPLE_STRIDE
      }
      y += CLUMP_SAMPLE_STRIDE
    }

    val mean = sum / samples
    assertTrue(
      kotlin.math.abs(mean - 1.0) < CLUMP_MEAN_TOLERANCE,
      "the clump field averages $mean over $samples samples, not one - every stand's capacity is off by " +
          "that factor"
    )
  }

  /**
   * The lattice is the minimum-spacing guarantee, and this is the number it guarantees.
   *
   * Two trunks in adjacent cells start [VegetationParams.cellSize] apart and each wanders up to
   * `cellSize * jitterShare / 2`, so the floor is `cellSize * (1 - jitterShare)` - 1.2 m at the defaults.
   * Asserted rather than assumed, because it is the only thing standing between the clump field and a
   * pair of trunks in the same square metre.
   */
  @Test
  fun `no two props stand closer than the lattice floor`() {
    val props = forestProps()
    val floor = config.let { PROP_CELL_SIZE * (1.0 - PROP_JITTER_SHARE) }

    assertTrue(props.size > 40, "only ${props.size} props in the block, which asserts nothing")

    var closest = Double.MAX_VALUE
    for (i in props.indices) {
      for (j in i + 1 until props.size) {
        val d = hypot(props[i].first - props[j].first, props[i].second - props[j].second)
        if (d < closest) closest = d
      }
    }

    assertTrue(
      closest >= floor - 1e-9,
      "two props stand $closest m apart, inside the $floor m the lattice is supposed to guarantee"
    )
  }

  /**
   * The property the earlier stratified-block design could not have: spacing that varies.
   *
   * One cell per `k x k` block would put every tree on a pitch - good jitter, no clumps, no gap wider
   * than a block - and read as an orchard. Two measurements, because either alone is satisfiable by the
   * wrong thing: the spread has to be wide *and* the extremes have to be far apart, so a distribution
   * tightly bunched around one value with a couple of outliers fails.
   */
  @Test
  fun `nearest-neighbour spacing is spread rather than on a pitch`() {
    val props = forestProps()
    assertTrue(props.size > 40, "only ${props.size} props in the block, which asserts nothing")

    val nearest = props.indices.map { i ->
      var best = Double.MAX_VALUE
      for (j in props.indices) {
        if (i == j) continue
        val d = hypot(props[i].first - props[j].first, props[i].second - props[j].second)
        if (d < best) best = d
      }
      best
    }

    val mean = nearest.average()
    val deviation = kotlin.math.sqrt(nearest.sumOf { (it - mean) * (it - mean) } / nearest.size)
    val variation = deviation / mean

    assertTrue(
      variation > MIN_SPACING_VARIATION,
      "nearest-neighbour spacing varies by only ${variation * 100}% of its $mean m mean, which is a pitch"
    )
    assertTrue(
      nearest.max() / nearest.min() > MIN_SPACING_RANGE,
      "the widest spacing (${nearest.max()} m) is under $MIN_SPACING_RANGE times the tightest " +
          "(${nearest.min()} m); a wood has thickets and glades in it"
    )
  }

  /**
   * A smaller entity share emits a strict subset of a larger one.
   *
   * The observable form of the property that makes `CANOPY_COVER` and a stand's advertised capacity two views
   * of one function: the thinning compares the *same* per-cell roll against a lower threshold, so lowering the
   * share can only ever remove trees, never move them or add them.
   *
   * It used to be checked against `candidatesIn`, the unthinned lattice the voxel path walked. That is gone
   * with the voxels, and this is the better test anyway - it asserts the monotonicity a reader would actually
   * rely on, and it does it through the public knob rather than through an internal buffer.
   */
  @Test
  fun `a smaller entity share emits a subset of a larger one`() {
    val sparse = propsBy { it.copy(entityShare = 0.10) }
    val dense = propsBy { it.copy(entityShare = 0.40) }

    assertTrue(sparse.isNotEmpty(), "the sparse scatter emitted nothing, so there is nothing to compare")
    assertTrue(dense.size > sparse.size, "a larger share emitted no more props than a smaller one")

    val missing = sparse.keys - dense.keys
    assertEquals(
      emptySet(), missing,
      "${missing.size} props exist at a 0.10 share and not at 0.40, so the two are different scatters " +
          "rather than one thinned twice"
    )

    // And identical where they overlap: same cell, same tree.
    for ((id, position) in sparse) {
      assertEquals(position, dense.getValue(id), "prop $id stands in a different place at a different share")
    }
  }

  /**
   * A prop belongs to exactly one chunk, and the chunk it belongs to is the one holding its trunk column.
   *
   * `vector/Aabb.contains` is a closed interval, so a bounds test would hand a trunk sitting exactly on a
   * chunk boundary to both of the chunks that share it - two entities where the world has one tree.
   */
  @Test
  fun `exactly one chunk claims each prop`() {
    val seen = HashMap<Long, ChunkPos>()

    for (offsetY in 0 until FOREST_BLOCK) {
      for (offsetX in 0 until FOREST_BLOCK) {
        val chunk = ChunkPos(FOREST_CHUNK + offsetX, FOREST_CHUNK + offsetY)
        val props = propsOf(chunk)

        for (i in props.indices) {
          val previous = seen.put(props.identityAt(i), chunk)
          assertTrue(previous == null, "${props.identityAt(i)} is claimed by both $previous and $chunk")

          // And the claim is the trunk's own column, not merely some chunk nearby.
          val columnX = Math.floorDiv(kotlin.math.floor(props.xAt(i) / VOXEL).toLong(), CHUNK_SIZE.toLong())
          val columnY = Math.floorDiv(kotlin.math.floor(props.yAt(i) / VOXEL).toLong(), CHUNK_SIZE.toLong())
          assertEquals(chunk.x.toLong(), columnX, "prop at ${props.xAt(i)} is not in the chunk that emitted it")
          assertEquals(chunk.y.toLong(), columnY, "prop at ${props.yAt(i)} is not in the chunk that emitted it")
        }
      }
    }

    assertTrue(seen.size > 40, "only ${seen.size} props across the block")
  }

  /**
   * The knob is proportional: halving the entity share roughly halves the props.
   *
   * What a `VEGETATION_STAND`'s advertised capacity is computed from, so if this drifts every stand in the
   * world under- or over-promises and both numbers still look plausible.
   *
   * Measured as a *ratio between two shares* rather than against an absolute expectation, because the
   * expectation needs the mean canopy cover over exactly the sampled ground - which is what
   * `Invariants.checkVegetationStandsAdvertiseFillableCapacity` does with real stands. Here the two runs share
   * their ground, so it cancels.
   */
  @Test
  fun `halving the entity share halves the props`() {
    val dense = propsBy { it.copy(entityShare = 0.40) }.size
    val sparse = propsBy { it.copy(entityShare = 0.20) }.size

    assertTrue(dense > 200, "only $dense props at the higher share, too few to measure a ratio against")

    val ratio = dense.toDouble() / sparse
    assertTrue(
      kotlin.math.abs(ratio - 2.0) < SHARE_RATIO_TOLERANCE,
      "halving the share changed the prop count by ${"%.2f".format(ratio)}x rather than 2x"
    )
  }

  @Test
  fun `nothing stands on worked ground`() {
    val chunkX = Math.floorDiv((MINE_X / VOXEL).toInt(), CHUNK_SIZE)
    val chunkY = Math.floorDiv((MINE_Y / VOXEL).toInt(), CHUNK_SIZE)
    val props = materializerOf(Double.NaN).propsIn(chunkX, chunkY)

    var inside = 0
    for (i in props.indices) {
      val distance = hypot(props.xAt(i) - MINE_X, props.yAt(i) - MINE_Y)
      if (distance < MINE_RADIUS * MineHead.COLLAR_SHARE) inside++
    }

    assertEquals(0, inside, "$inside props stand on the mine head's collar")
  }

  /**
   * Nothing stands in standing water.
   *
   * Stronger on props than it was on voxels. The voxel version passed partly for the wrong reason - a drowned
   * column has no air in it, so there was nowhere to write a canopy even with the veto deleted - which is why
   * it used shallow water. This asserts the veto directly: the prop is either emitted or it is not.
   */
  @Test
  fun `nothing stands in standing water`() {
    val level = groundAt(FOREST_CHUNK_EAST_EDGE) + 3.0
    val materializer = materializerOf(waterLevel = level)

    var drowned = 0
    for (offsetY in 0 until FOREST_BLOCK) {
      for (offsetX in 0 until FOREST_BLOCK) {
        val props = materializer.propsIn(FOREST_CHUNK + offsetX, FOREST_CHUNK + offsetY)
        for (i in props.indices) if (props.groundAt(i) < level) drowned++
      }
    }

    assertEquals(0, drowned, "$drowned props stand under water")
  }

  /** Mean length of a `true` run, in samples. Zero when there are none. */
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

  private fun groundAt(worldX: Double) = GROUND_AT_ORIGIN + SLOPE * worldX

  private fun slopedColumns() = ChunkColumnSource { chunk, halo ->
    ColumnHeights.build(chunk, config.chunkSize, halo) { localX, localY ->
      groundAt(config.columnCenter(chunk, localX, localY).first)
    }
  }

  private fun scatterOf() = VegetationScatter(config, surfaceOf(Double.NaN), SEED)

  private fun propsOf(chunk: ChunkPos): PropInstances {
    val props = PropInstances()
    scatterOf().propsIn(chunk, PropSite { worldX, _ -> groundAt(worldX) }, props)
    return props
  }

  /** Every prop over the forest block under a variant of the default tuning, keyed by its durable name. */
  private fun propsBy(tune: (VegetationParams) -> VegetationParams): Map<Long, Pair<Double, Double>> {
    val scatter = VegetationScatter(config, surfaceOf(Double.NaN), SEED, tune(VegetationParams()))
    val out = HashMap<Long, Pair<Double, Double>>()

    for (offsetY in 0 until SHARE_SAMPLES) {
      for (offsetX in 0 until SHARE_SAMPLES) {
        val chunk = ChunkPos(SHARE_FIRST_CHUNK + offsetX * SHARE_STRIDE, SHARE_FIRST_CHUNK + offsetY * SHARE_STRIDE)
        val props = PropInstances()
        scatter.propsIn(chunk, PropSite { worldX, _ -> groundAt(worldX) }, props)
        for (i in props.indices) out[props.identityAt(i)] = props.xAt(i) to props.yAt(i)
      }
    }

    return out
  }

  /** Whether the voxel column at a world position belongs to [chunk] - the emitter's ownership test. */
  private fun ownedBy(chunk: ChunkPos, worldX: Double, worldY: Double): Boolean {
    val columnX = Math.floorDiv(kotlin.math.floor(worldX / VOXEL).toLong(), CHUNK_SIZE.toLong())
    val columnY = Math.floorDiv(kotlin.math.floor(worldY / VOXEL).toLong(), CHUNK_SIZE.toLong())
    return columnX == chunk.x.toLong() && columnY == chunk.y.toLong()
  }

  /** Every prop position over the forest block, as `(x, y)` pairs. */
  private fun forestProps(): List<Pair<Double, Double>> {
    val out = mutableListOf<Pair<Double, Double>>()

    for (offsetY in 0 until FOREST_BLOCK) {
      for (offsetX in 0 until FOREST_BLOCK) {
        val props = propsOf(ChunkPos(FOREST_CHUNK + offsetX, FOREST_CHUNK + offsetY))
        for (i in props.indices) out.add(props.xAt(i) to props.yAt(i))
      }
    }

    return out
  }

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

    // --- The prop lattice ---------------------------------------------------------------------------
    // Read off VegetationParams' defaults rather than duplicated as opinions: these tests assert what the
    // shipped tuning does, so a knob moving should move them with it.
    val PROP_CELL_SIZE = VegetationParams().cellSize
    val PROP_JITTER_SHARE = VegetationParams().jitterShare
    val PROP_ENTITY_SHARE = VegetationParams().entityShare

    /** Metres across the area the clump mean is measured over: a few hundred wavelengths. */
    const val CLUMP_SAMPLE_SPAN = 4000.0

    /** Deliberately not a divisor of the wavelength, so the samples do not land on one phase. */
    const val CLUMP_SAMPLE_STRIDE = 7.3

    /**
     * How far the measured clump mean may sit from one.
     *
     * Two per cent. Gradient noise has a mean of zero by symmetry, so this is sampling error over a finite
     * area rather than a fudge factor - and it is tight enough that a field accidentally built on
     * `(fbm+1)/2` (mean a half) or on `fbm` alone (mean zero) fails by an order of magnitude.
     */
    const val CLUMP_MEAN_TOLERANCE = 0.02

    /**
     * Coefficient of variation the nearest-neighbour spacing must exceed.
     *
     * A regular pitch scores zero and a Poisson field scores about 0.52. One cell per `k x k` block lands
     * near 0.15 - the in-block choice jitters the pitch without ever clumping - so 0.30 sits between the
     * design that was rejected and the one that was kept.
     */
    const val MIN_SPACING_VARIATION = 0.30

    /** Widest nearest-neighbour distance over the tightest. A pitch scores about 1. */
    const val MIN_SPACING_RANGE = 3.0

    /**
     * Sampled chunks per axis for the retained share.
     *
     * Twenty squared, because a sixteen-metre chunk holds only sixteen lattice cells and therefore about
     * six trees - so the sample size is set by how many trees are needed for the binomial noise to sit well
     * inside [SHARE_TOLERANCE], not by how many chunks feel like enough.
     */
    const val SHARE_SAMPLES = 20

    /**
     * Chunks between samples: 37 x 16 m is 592 m, about twenty clump wavelengths, so no two sampled chunks
     * see a correlated phase of the field. Deliberately not a round number of wavelengths.
     */
    const val SHARE_STRIDE = 37

    /** Leaves the twelve strided samples inside the 16 km fixture world with room either side. */
    const val SHARE_FIRST_CHUNK = 125

    /**
     * Slack on the two-to-one prop ratio between a 0.40 share and a 0.20 one.
     *
     * Binomial noise over a few thousand props, plus the clamp in `treeAt` biting slightly harder at the
     * higher share where `entityShare * clumpAt` more often exceeds one - which is a real effect and is why
     * this is not tighter.
     */
    const val SHARE_RATIO_TOLERANCE = 0.25

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

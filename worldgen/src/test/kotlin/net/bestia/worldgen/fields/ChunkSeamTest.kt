package net.bestia.worldgen.fields

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkHeightSampler
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.geo.DropletHeightField
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chunk-boundary stress view, run as a test.
 *
 * A 4x4 block of chunks is generated independently, on four threads, in a shuffled order, and every
 * column that two chunks share must come out identical. The tolerance is zero, not "close enough":
 * both chunks run the same code on the same inputs, so anything other than bit-identical means
 * something in the pipeline saw which chunk it was being generated for.
 *
 * The last two tests are negative controls. A harness that cannot fail is worth nothing, and a seam
 * check that had quietly stopped detecting seams would be worse than no seam check at all.
 */
class ChunkSeamTest {

  private val config = WorldConfig(
    seed = 0xBE571AL,
    widthCells = 64,
    heightCells = 64,
    chunkSize = 32,
    voxelSize = 1.0
  )

  /** Multi-octave terrain, a pure function of world position - the only kind that is seam-free. */
  private val terrain = BaseHeightField { x, y ->
    120.0 + Noise.fbm(config.seed, x, y, octaves = 6, frequency = 1.0 / 220.0) * 45.0
  }

  private fun storeWith(stage: String, features: List<VectorFeature>): FeatureStore {
    val store = FeatureStore()
    store.add(StageId(stage), features)
    store.freeze()
    return store
  }

  private fun emptyStore(): FeatureStore = FeatureStore().apply { freeze() }

  /** Largest amount by which any column in [chunk] was moved away from the bare terrain. */
  private fun deepestCarve(sampler: ChunkHeightSampler, chunk: ChunkPos): Double {
    val heights = sampler.heights(chunk)
    var worst = 0.0

    for (y in 0 until config.chunkSize) {
      for (x in 0 until config.chunkSize) {
        val (worldX, worldY) = config.columnCenter(chunk, x, y)
        worst = maxOf(worst, abs(heights[x, y] - terrain.heightAt(worldX, worldY)))
      }
    }

    return worst
  }

  @Test
  fun `bare noise terrain has no seams`() {
    val sampler = ChunkHeightSampler(config, terrain, emptyStore())

    val report = ChunkSeamCheck.run(sampler, blockSize = 4, threads = 4)

    assertTrue(report.isClean, describe(report))
    assertEquals(16, report.chunksChecked)
    assertTrue(report.columnsCompared > 0)
  }

  @Test
  fun `a meandering river stays continuous across every chunk border it crosses`() {
    // Runs diagonally across the whole 4x4 block, so it crosses both kinds of border and several
    // corners. The meander is applied to the one continuous centerline, at vector level - which is
    // precisely the thing that cannot be done per chunk, because each chunk would perturb the
    // channel independently and the river would visibly jump at every border.
    val course = Polyline(listOf(Vec2d(-40.0, -20.0), Vec2d(200.0, 160.0)))
      .resample(8.0)
      .offsetLaterally { s -> 14.0 * sin(s / 55.0) + 5.0 * sin(s / 17.0) }

    val river = LinearFeatures.river(
      id = FeatureId(1),
      centerline = course,
      stationSpacing = 6.0,
      // Bed follows the terrain along the centerline, so the channel is genuinely cut in
      // everywhere rather than only where the ground happens to be high.
      bedElevation = { s -> course.pointAt(s).let { terrain.heightAt(it.x, it.y) } },
      width = { 11.0 },
      depth = { 3.5 },
      shoulder = { 18.0 }
    )

    val store = storeWith("hydrology", listOf(river))
    val sampler = ChunkHeightSampler(config, terrain, store)

    val report = ChunkSeamCheck.run(sampler, blockSize = 4, threads = 4)
    assertTrue(report.isClean, describe(report))

    // Guard against a vacuous pass: the river has to actually be cutting into these chunks.
    assertTrue(
      deepestCarve(sampler, ChunkPos(1, 1)) > 1.0,
      "the river did not carve anything into chunk (1,1)"
    )
  }

  @Test
  fun `overlapping features of different priority stay continuous`() {
    // A river running along a deglaciated trough floor, with a road crossing both - the exact
    // feature-feature interaction the priority ordering exists for.
    val trough = LinearFeatures.glacialTrough(
      id = FeatureId(10),
      centerline = Polyline(listOf(Vec2d(-60.0, 40.0), Vec2d(220.0, 70.0))),
      stationSpacing = 10.0,
      floorElevation = { 96.0 },
      halfWidthFloor = { 18.0 },
      halfWidth = { 55.0 },
      wallHeight = { 40.0 }
    )
    val river = LinearFeatures.river(
      id = FeatureId(11),
      centerline = Polyline(listOf(Vec2d(-60.0, 45.0), Vec2d(220.0, 66.0))),
      stationSpacing = 6.0,
      bedElevation = { 96.0 },
      width = { 7.0 },
      depth = { 2.5 },
      shoulder = { 9.0 }
    )
    val track = LinearFeatures.road(
      id = FeatureId(12),
      centerline = Polyline(listOf(Vec2d(20.0, -40.0), Vec2d(70.0, 200.0))),
      stationSpacing = 5.0,
      surfaceElevation = { 118.0 },
      halfWidth = { 3.0 },
      shoulder = { 9.0 },
      endTaper = 20.0
    )

    val store = storeWith("mixed", listOf(trough, river, track))
    val sampler = ChunkHeightSampler(config, terrain, store)

    val report = ChunkSeamCheck.run(sampler, blockSize = 4, threads = 4)

    assertTrue(report.isClean, describe(report))
    assertTrue(deepestCarve(sampler, ChunkPos(1, 1)) > 1.0, "no feature reached chunk (1,1)")
  }

  @Test
  fun `generation order and thread count do not change the result`() {
    val sampler = ChunkHeightSampler(config, terrain, emptyStore())

    val single = ChunkSeamCheck.run(sampler, blockSize = 3, threads = 1, shuffleSeed = 1L)
    val many = ChunkSeamCheck.run(sampler, blockSize = 3, threads = 8, shuffleSeed = 2L)

    assertTrue(single.isClean && many.isClean)
    assertEquals(single.columnsCompared, many.columnsCompared)
  }

  @Test
  fun `the seam check catches detail noise seeded from the chunk`() {
    // The classic mistake: per-chunk detail erosion seeded from the chunk coordinate, with no
    // overlap margin and no blend. Every column still gets a plausible height; the chunks simply do
    // not agree where they meet.
    val chunkSeeded = ChunkColumnSource { chunk, halo ->
      val detailSeed = GenRng.hash(config.seed, chunk.x.toLong(), chunk.y.toLong())
      ColumnHeights.build(chunk, config.chunkSize, halo) { localX, localY ->
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        terrain.heightAt(worldX, worldY) +
            Noise.fbm(detailSeed, worldX, worldY, octaves = 4, frequency = 1.0 / 40.0) * 12.0
      }
    }

    val report = ChunkSeamCheck.run(chunkSeeded, blockSize = 4, threads = 4)

    assertTrue(!report.isClean, "the seam check failed to notice chunk-seeded detail noise")
    assertTrue(report.worstDelta > 1.0, "worst delta was only ${report.worstDelta}")
  }

  @Test
  fun `the seam check catches a feature perturbed per chunk`() {
    // The other classic mistake: meandering the channel inside chunk generation instead of on the
    // centerline. The offset is plausible and smooth within a chunk and discontinuous across it.
    val course = Polyline(listOf(Vec2d(-40.0, -20.0), Vec2d(200.0, 160.0))).resample(8.0)
    // Flat ground, so the channel is guaranteed to be cut everywhere and the only thing the check
    // can be reacting to is the per-chunk phase.
    val plain = BaseHeightField { _, _ -> 130.0 }

    val perChunkMeander = ChunkColumnSource { chunk, halo ->
      val meanderPhase = GenRng.hashUnit(chunk.x.toLong(), chunk.y.toLong()) * 6.283
      val river = LinearFeatures.river(
        id = FeatureId(1),
        centerline = course.offsetLaterally { s -> 14.0 * sin(s / 55.0 + meanderPhase) },
        stationSpacing = 6.0,
        bedElevation = { 130.0 },
        width = { 11.0 },
        depth = { 3.5 },
        shoulder = { 18.0 }
      )
      val store = FeatureStore().apply { add(StageId("bad-hydrology"), listOf(river)); freeze() }

      ChunkHeightSampler(config, plain, store).heights(chunk, halo)
    }

    val report = ChunkSeamCheck.run(perChunkMeander, blockSize = 4, threads = 4)

    assertTrue(!report.isClean, "the seam check failed to notice a per-chunk meander")
  }

  @Test
  fun `the seam check catches droplet tiles keyed on the chunk`() {
    // The negative control for `geo/DropletHeightField`, and the reason its tile lattice is fixed in world space.
    //
    // The tempting design is a droplet simulation per chunk with a margin, blended in the overlap. It cannot
    // work, and this is what it looks like when it does not: each chunk offsets the lattice to its own origin,
    // so two chunks simulate the same ground independently and their results differ. The real field takes no
    // chunk parameter at all, which is what makes that mistake unexpressible rather than merely avoided - so
    // the control has to be built here, at the level that *does* know which chunk it is.
    val chunkKeyed = ChunkColumnSource { chunk, halo ->
      val field = DropletHeightField(
        // Shifting the inner field by the chunk origin is equivalent to shifting the lattice under it.
        BaseHeightField { x, y ->
          terrain.heightAt(x + chunk.x * config.chunkExtent, y + chunk.y * config.chunkExtent)
        },
        config.seed,
        DropletParams(enabled = true, tileExtent = config.chunkExtent, cellSize = 2.0)
      )
      ColumnHeights.build(chunk, config.chunkSize, halo) { localX, localY ->
        val (worldX, worldY) = config.columnCenter(chunk, localX, localY)
        field.heightAt(worldX, worldY)
      }
    }

    val report = ChunkSeamCheck.run(chunkKeyed, blockSize = 4, threads = 4)

    assertTrue(!report.isClean, "the seam check failed to notice chunk-keyed droplet tiles")
  }

  private fun describe(report: ChunkSeamCheck.Report) =
    report.toString() + report.seams.take(5).joinToString("\n", prefix = "\n")
}


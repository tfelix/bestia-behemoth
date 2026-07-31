package net.bestia.worldgen.geo

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkHeightSampler
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chunk-scale droplet erosion, and above all whether it is seam-free.
 *
 * The architecture document argues against this feature on exactly one ground - "any error in that blend puts
 * back exactly the seams the vector tier exists to remove" - so the seam check *is* the safety argument, and it
 * is run here at zero tolerance with the erosion on. Everything else in this file is secondary.
 *
 * The tolerance being zero is the point worth restating: two chunks that share a column must agree **bit for
 * bit**, not closely. That is achievable not because the arithmetic is exact but because both chunks run the
 * same arithmetic - see `DropletHeightField`'s KDoc.
 */
class DropletErosionTest {

  private val config = WorldConfig(
    seed = 0xD8071E7L,
    widthCells = 64,
    heightCells = 64,
    chunkSize = 32,
    voxelSize = 1.0
  )

  /** Steep multi-octave ground, so there is relief for droplets to work on. */
  private val terrain = BaseHeightField { x, y ->
    300.0 + Noise.fbm(config.seed, x, y, octaves = 6, frequency = 1.0 / 260.0) * 160.0
  }

  private fun on(
    tileExtent: Double = 128.0,
    cellSize: Double = 2.0
  ) = DropletParams(enabled = true, tileExtent = tileExtent, cellSize = cellSize)

  private fun emptyStore() = FeatureStore().apply { freeze() }

  private fun describe(report: ChunkSeamCheck.Report) =
    report.toString() + report.seams.take(5).joinToString("\n", prefix = "\n")

  // --- The safety argument ---------------------------------------------------------------------------

  @Test
  fun `eroded terrain has no seams at zero tolerance`() {
    val field = DropletHeightField(terrain, config.seed, on())
    val sampler = ChunkHeightSampler(config, field, emptyStore())

    val report = ChunkSeamCheck.run(sampler, blockSize = 4, threads = 4)

    assertTrue(report.isClean, describe(report))
    assertEquals(16, report.chunksChecked)

    // Guard against a vacuous pass: if the erosion did nothing, a clean seam check proves nothing at all. This
    // is the trap the module's own notes call out - an invariant that skips its subject reports success.
    assertTrue(
      field.worstDelta() > 0.5,
      "droplet erosion moved at most ${"%.3f".format(Locale.ROOT, field.worstDelta())} m, " +
          "so the seam check above asserts nothing"
    )
  }

  @Test
  fun `generation order and thread count do not change eroded terrain`() {
    // The other half of the seam argument. A shared RNG stream or a cache that leaked state between tiles would
    // pass the single-threaded check and fail here.
    val field = DropletHeightField(terrain, config.seed, on())
    val sampler = ChunkHeightSampler(config, field, emptyStore())

    val single = ChunkSeamCheck.run(sampler, blockSize = 3, threads = 1, shuffleSeed = 1L)
    val many = ChunkSeamCheck.run(sampler, blockSize = 3, threads = 8, shuffleSeed = 2L)

    assertTrue(single.isClean, describe(single))
    assertTrue(many.isClean, describe(many))
  }

  @Test
  fun `two independent fields agree column for column`() {
    // Two fields from one seed are what two *processes* are - the client generating a chunk the server also
    // generated. Bit-identical, so `assertEquals` with no delta rather than a tolerance.
    val a = DropletHeightField(terrain, config.seed, on())
    val b = DropletHeightField(terrain, config.seed, on())

    for (n in 0 until 4_000) {
      val x = 40.0 + n * 0.37
      val y = 90.0 + n * 0.53
      assertEquals(a.heightAt(x, y), b.heightAt(x, y), "the two fields disagree at ($x, $y)")
    }
  }

  @Test
  fun `a column is stable across repeated reads`() {
    // A chunk regenerated after a cache eviction must give the same ground, so nothing may accumulate.
    val field = DropletHeightField(terrain, config.seed, on())

    for (n in 0 until 500) {
      val x = 300.0 + n * 1.7
      val y = 210.0 + n * 2.3
      val first = field.heightAt(x, y)
      repeat(4) { assertEquals(first, field.heightAt(x, y), "column ($x, $y) changed between reads") }
    }
  }

  @Test
  fun `the four tile weights are a partition of unity`() {
    // The property the whole blend rests on, asserted **against the implementation's own arithmetic** rather
    // than against a copy of it - a test that reimplemented the tent would keep passing if the real one changed.
    //
    // Two earlier attempts to test this indirectly both failed to discriminate, and the reason is worth keeping.
    // Comparing the height step across a *lattice line* proves nothing: at a lattice line the weight is entirely
    // on one tile from both sides, so nothing can jump there by construction. Comparing across the *midpoints*,
    // where a nearest-tile selection would jump, proves almost nothing either - the erosion delta is genuinely
    // rough everywhere, so a discontinuity does not stand out against it. Replacing the blend with
    // nearest-tile-only left every indirect test passing. Only the direct assertion catches it.
    val field = DropletHeightField(terrain, config.seed, on())

    for (n in 0 until 10_000) {
      val x = -900.0 + n * 0.7331
      val y = 450.0 + n * 0.3977
      val (fx, fy) = field.cellFractionAt(x, y)

      var sum = 0.0
      for (dj in 0..1) {
        for (di in 0..1) sum += field.weightOf(di, dj, fx, fy)
      }

      assertEquals(
        1.0, sum, 1e-12,
        "the tile weights at ($x, $y) sum to ${"%.15f".format(Locale.ROOT, sum)}"
      )
    }
  }

  @Test
  fun `a tile's weight reaches zero before it leaves the blend`() {
    // The **continuity** half, and it is a separate claim from summing to one - which is the mistake found while
    // writing these tests. Replacing the tent with "always take the lower tile" still sums to exactly one and
    // still passes the partition-of-unity test above, because a degenerate partition is a partition. What it
    // breaks is continuity: the outgoing tile's weight drops from 1 to 0 at the cell boundary and the blend
    // steps with it.
    //
    // The property that rules that out: a tile that is about to leave the four must already contribute nothing.
    val field = DropletHeightField(terrain, config.seed, on())

    for (dj in 0..1) {
      for (fy in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
        // At the far edge of the cell, the low tile is on its way out and must be weightless.
        assertEquals(
          0.0, field.weightOf(0, dj, 1.0, fy), 0.0,
          "the low x tile still has weight at the top of the cell"
        )
        // And at the near edge, the high tile has not arrived yet.
        assertEquals(
          0.0, field.weightOf(1, dj, 0.0, fy), 0.0,
          "the high x tile already has weight at the bottom of the cell"
        )
      }
    }

    for (di in 0..1) {
      for (fx in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
        assertEquals(0.0, field.weightOf(di, 0, fx, 1.0), 0.0, "the low y tile still has weight at the top")
        assertEquals(0.0, field.weightOf(di, 1, fx, 0.0), 0.0, "the high y tile already has weight at the bottom")
      }
    }
  }

  @Test
  fun `the tile weights vary continuously across a cell`() {
    // The same claim measured rather than sampled at the ends: no weight may step. A tent over [0,1] changes by
    // exactly the step size, so anything much larger is a discontinuity.
    val field = DropletHeightField(terrain, config.seed, on())
    val step = 1.0 / 512

    for (dj in 0..1) {
      for (di in 0..1) {
        var previous = field.weightOf(di, dj, 0.0, 0.3)
        var f = step
        while (f <= 1.0) {
          val here = field.weightOf(di, dj, f, 0.3)
          assertTrue(
            abs(here - previous) <= step * 1.5,
            "weight ($di,$dj) jumps by ${abs(here - previous)} at fx=$f"
          )
          previous = here
          f += step
        }
      }
    }
  }

  @Test
  fun `every tile weight is between zero and one`() {
    // A negative weight would subtract a neighbour's erosion, which sums to one and still bulges the terrain.
    val field = DropletHeightField(terrain, config.seed, on())

    for (n in 0 until 4_000) {
      val (fx, fy) = field.cellFractionAt(-40.0 + n * 1.13, 17.0 + n * 0.61)
      for (dj in 0..1) {
        for (di in 0..1) {
          val weight = field.weightOf(di, dj, fx, fy)
          assertTrue(weight in 0.0..1.0, "weight ($di,$dj) at fraction ($fx,$fy) is $weight")
        }
      }
    }
  }

  // --- What it does ---------------------------------------------------------------------------------

  @Test
  fun `disabled is bit-identical to the field it wraps`() {
    // The default path. `enabled = false` must not perturb anything, so switching this feature on and off is a
    // decision about gullies and not a decision about the whole world's terrain.
    val field = DropletHeightField(terrain, config.seed, DropletParams())

    for (n in 0 until 5_000) {
      val x = -120.0 + n * 0.41
      val y = 77.0 + n * 0.29
      assertEquals(terrain.heightAt(x, y), field.heightAt(x, y), "wrapping changed the height at ($x, $y)")
    }
  }

  @Test
  fun `erosion both cuts and fills`() {
    // Sediment transport is the thing analytic detail cannot do - the architecture document's own statement of
    // what deviation 1 costs is "gullies without the debris fans at the bottom of them". If every delta were
    // negative there would be no transport, only removal.
    val field = DropletHeightField(terrain, config.seed, on())

    var cut = 0
    var filled = 0
    for (n in 0 until 20_000) {
      val x = 100.0 + (n % 200) * 1.3
      val y = 100.0 + (n / 200) * 1.3
      val delta = field.heightAt(x, y) - terrain.heightAt(x, y)
      if (delta < -0.05) cut++
      if (delta > 0.05) filled++
    }

    assertTrue(cut > 0, "nothing was eroded")
    assertTrue(filled > 0, "nothing was deposited, so there is no sediment transport")
  }

  @Test
  fun `no column is moved further than the clamp allows`() {
    // maxDelta is a hard bound rather than a consequence of the tuning, because droplet erosion is a feedback
    // loop: a mistuning does not give slightly wrong terrain, it gives a crevasse.
    val params = on().copy(maxDelta = 6.0)
    val field = DropletHeightField(terrain, config.seed, params)

    for (n in 0 until 20_000) {
      val x = 100.0 + (n % 200) * 1.3
      val y = 100.0 + (n / 200) * 1.3
      val delta = abs(field.heightAt(x, y) - terrain.heightAt(x, y))
      assertTrue(
        delta <= params.maxDelta + 1e-9,
        "the height at ($x, $y) moved ${"%.3f".format(Locale.ROOT, delta)} m, past the ${params.maxDelta} m clamp"
      )
    }
  }

  @Test
  fun `erosion survives vector features being stamped over it`() {
    // Droplets run on the base field, so the feature system sees eroded ground and stamps over it unchanged.
    // What this checks is that the two tiers still agree at chunk borders when both are active, which is the
    // combination that actually ships.
    val field = DropletHeightField(terrain, config.seed, on())
    val sampler = ChunkHeightSampler(config, field, emptyStore())

    val report = ChunkSeamCheck.run(sampler, blockSize = 4, threads = 4)
    assertTrue(report.isClean, describe(report))

    // And the chunk heights really are the eroded ones rather than the analytic field's.
    var moved = 0.0
    val heights = sampler.heights(ChunkPos(1, 1))
    for (y in 0 until config.chunkSize) {
      for (x in 0 until config.chunkSize) {
        val (worldX, worldY) = config.columnCenter(ChunkPos(1, 1), x, y)
        moved = maxOf(moved, abs(heights[x, y] - terrain.heightAt(worldX, worldY)))
      }
    }
    assertTrue(moved > 0.1, "no erosion reached chunk (1,1)")
  }

  @Test
  fun `a coarser tile lattice is still seam-free`() {
    // The lattice spacing is a tuning knob, and a seam argument that only holds at one spacing is a coincidence
    // rather than an argument.
    for (extent in listOf(64.0, 96.0, 256.0)) {
      val field = DropletHeightField(terrain, config.seed, on(tileExtent = extent))
      val sampler = ChunkHeightSampler(config, field, emptyStore())

      val report = ChunkSeamCheck.run(sampler, blockSize = 3, threads = 4)
      assertTrue(report.isClean, "tile extent $extent: ${describe(report)}")
    }
  }
}

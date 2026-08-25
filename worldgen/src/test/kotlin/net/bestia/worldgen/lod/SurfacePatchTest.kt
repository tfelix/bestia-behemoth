package net.bestia.worldgen.lod

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three promises a coarse patch makes, asserted rather than eyeballed.
 *
 * A patch exists to be drawn *next to* real chunks, so the one that matters is the first: the two must not
 * disagree about where the ground is, or the far ring meets the near ring in a visible step. It holds by
 * construction - both read `base.heightAt` and stamp the same features - and this is what says so out loud,
 * because "by construction" stops being true the moment somebody adds a term to one of the two paths.
 */
class SurfacePatchTest {

  /** Small enough to build quickly, large enough for the stages to produce real terrain to sample. */
  private fun config(seed: Long) = WorldConfig(
    seed = seed,
    widthCells = 160,
    heightCells = 160,
    chunkSize = 32,
    voxelSize = 1.0
  )

  private val world by lazy { StandardWorld.build(config(0xC0FFEEL)) }

  private val sampler by lazy { SurfacePatchSampler.of(world) }

  @Test
  fun `a patch sample sits on the terrain the chunk tier would generate`() {
    val patch = sampler.sample(PatchPos(0, 4, 4))

    // Level 0 samples every fourth metre, so every sample lands on a whole voxel column. Compare against
    // the very heightfield chunk generation lifts terrain through - not a second copy of it.
    var checked = 0
    for (j in 0 until PatchGrid.SAMPLES step 8) {
      for (i in 0 until PatchGrid.SAMPLES step 8) {
        val worldX = patch.pos.worldX(i)
        val worldY = patch.pos.worldY(j)

        val chunkX = world.config.chunkOf(worldX)
        val chunkY = world.config.chunkOf(worldY)
        val heights = world.columns.heights(ChunkPos(chunkX, chunkY, 0), 0)

        val localX = Math.floorMod(world.config.voxelOf(worldX), world.config.chunkSize.toLong()).toInt()
        val localY = Math.floorMod(world.config.voxelOf(worldY), world.config.chunkSize.toLong()).toInt()

        // Not equality: a patch samples the column's corner and `ColumnHeights` samples its centre, so they
        // are half a voxel apart horizontally. What must not happen is the two disagreeing by a step.
        val delta = abs(patch.heightAt(i, j) - heights[localX, localY])
        assertTrue(
          delta < 1.5,
          "patch height ${patch.heightAt(i, j)} and column height ${heights[localX, localY]} at " +
              "($worldX,$worldY) differ by $delta - the far ring would meet the near ring in a step"
        )
        checked++
      }
    }

    assertTrue(checked > 0, "the sweep asserted nothing")
  }

  @Test
  fun `neighbouring patches agree on the edge they share`() {
    val left = sampler.sample(PatchPos(0, 4, 4))
    val right = sampler.sample(PatchPos(0, 5, 4))

    // Sample CELLS of one patch and sample 0 of the next are the same world position by construction, which
    // is what lets two patches meet with no stitching geometry between them.
    for (j in 0 until PatchGrid.SAMPLES) {
      assertEquals(
        left.heightAt(PatchGrid.CELLS, j), right.heightAt(0, j),
        "row $j of the shared edge disagrees"
      )
    }
  }

  @Test
  fun `a coarser level samples a subset of the finer one`() {
    val fine = sampler.sample(PatchPos(0, 4, 4))
    val coarse = sampler.sample(PatchPos(1, 2, 2))

    // Level 1 spans twice as much ground at twice the spacing, so its left half covers the whole of the
    // level-0 patch above and every second sample of that half is a level-0 sample.
    for (j in 0..PatchGrid.CELLS / 2) {
      for (i in 0..PatchGrid.CELLS / 2) {
        assertEquals(
          fine.heightAt(i * 2, j * 2), coarse.heightAt(i, j),
          "level 1 sample ($i,$j) should be level 0 sample (${i * 2},${j * 2})"
        )
      }
    }
  }

  @Test
  fun `a patch round-trips through the codec`() {
    val patch = sampler.sample(PatchPos(0, 4, 4))
    val decoded = SurfacePatchCodec.decode(SurfacePatchCodec.encode(patch))

    assertEquals(patch.pos, decoded.pos)

    for (i in patch.height.indices) {
      assertTrue(
        abs(patch.height[i] - decoded.height[i]) <= SurfacePatchCodec.HEIGHT_STEP,
        "sample $i lost more than one quantisation step: ${patch.height[i]} became ${decoded.height[i]}"
      )
      assertEquals(patch.block[i], decoded.block[i], "sample $i changed material")
      assertEquals(patch.canopy[i], decoded.canopy[i], "sample $i changed canopy")
      assertEquals(
        patch.water[i].isNaN(), decoded.water[i].isNaN(),
        "sample $i changed from wet to dry or back"
      )
    }
  }

  /**
   * The budget this whole design rests on, so a format change that doubles it fails here rather than in
   * somebody's bandwidth graph.
   *
   * A patch covers sixty-four chunks, about three kilobytes each deflated. Measured over eighty-one patches
   * of the reference world the worst deflated to **2 179 bytes** against the ~192 kB those chunks would cost,
   * and the median is a few hundred - most ground is flatter than the worst of it. The bound is set at three
   * times the measured worst, so it catches a format regression rather than variation between seeds.
   */
  @Test
  fun `a deflated patch is a small fraction of the chunks it replaces`() {
    var worst = 0
    var worstAt: PatchPos? = null

    // A sweep, not one patch. Most of a world is flat water and deflates to nothing, so a single sample
    // would report a number that says more about where it landed than about the format.
    for (y in 2..10) {
      for (x in 2..10) {
        val pos = PatchPos(0, x, y)
        val payload = SurfacePatchCodec.encode(sampler.sample(pos))
        assertEquals(SurfacePatchCodec.encodedSize(), payload.size, "the payload is a fixed size")

        val deflated = deflate(payload)
        if (deflated > worst) { worst = deflated; worstAt = pos }
      }
    }
    assertTrue(
      worst < 6_000,
      "the worst of 81 patches deflated to $worst bytes at $worstAt, against ~192 kB for the 64 chunks " +
          "each one stands in for"
    )
  }

  private fun deflate(bytes: ByteArray): Int {
    val deflater = Deflater(9)
    return try {
      deflater.setInput(bytes)
      deflater.finish()

      val buffer = ByteArray(bytes.size + 64)
      var total = 0
      while (!deflater.finished()) total += deflater.deflate(buffer, 0, buffer.size)
      total
    } finally {
      deflater.end()
    }
  }
}

package net.bestia.worldgen.lod

/**
 * The visible surface of a square of ground, sampled on a regular grid instead of materialised into voxels.
 *
 * What a client draws past the range where it is sent real chunks. Four planes, one value per sample, and
 * between them they are everything a distant hillside needs: where the ground is, where standing water is,
 * what the ground is made of, and how wooded it is.
 *
 * ### What it deliberately does not carry
 *
 * No caves, no overhangs, no ore, no strata, no props. A patch is a *heightfield* - one surface per column -
 * so it cannot express a hole, and it does not try to. That is what makes it cheap, and it is also why the
 * full-detail ring cannot be replaced by it: you can look at a cave mouth from a kilometre away, but you
 * cannot walk into one you were only sent a patch of.
 *
 * It also carries nothing a player could not see. Ore is under the ground and cave interiors are inside it;
 * both are absent here for the same reason the world seed is absent from the wire.
 *
 * ### Player edits are not in it
 *
 * A patch comes from the heightfield, which is immutable, so digging does not move it and it needs no
 * revision. `ChunkService.surfaceElevationAt` already makes the same trade on the movement path. At four
 * metres a sample the largest thing a player can dig is smaller than the grid.
 */
class SurfacePatch(
  val pos: PatchPos,

  /**
   * Terrain surface elevation per sample, in metres. Absolute, like every other elevation in the generator.
   *
   * Defined everywhere, sea floor included - [water] is what says whether you can see the ground or the
   * water over it.
   */
  val height: FloatArray,

  /**
   * Standing water surface per sample in metres, or [NO_WATER] where the ground is dry.
   *
   * Sea and lakes only. River water is the one water body whose surface is not level, so it comes from a
   * per-chunk sampler rather than from the world tier - and at four metres a sample a channel is a sample
   * or two wide, which is a line of dots rather than a river. Distant rivers want the map's own treatment,
   * not this one.
   */
  val water: FloatArray,

  /** `BlockType.id` of the topmost block per sample, which is what decides how it is drawn. */
  val block: ByteArray,

  /** Canopy cover per sample as `0..255`, the same void fraction `LayerId.CANOPY_COVER` holds. */
  val canopy: ByteArray
) {

  init {
    val expected = PatchGrid.SAMPLES * PatchGrid.SAMPLES
    require(height.size == expected && water.size == expected) {
      "a patch holds $expected samples, got ${height.size} heights and ${water.size} waters"
    }
    require(block.size == expected && canopy.size == expected) {
      "a patch holds $expected samples, got ${block.size} blocks and ${canopy.size} canopy values"
    }
  }

  fun heightAt(i: Int, j: Int) = height[index(i, j)]

  fun waterAt(i: Int, j: Int) = water[index(i, j)]

  /** Whether this sample stands under water. Not `!= NO_WATER`: the sentinel is a NaN, so it never equals. */
  fun hasWaterAt(i: Int, j: Int) = !water[index(i, j)].isNaN()

  override fun toString() = "SurfacePatch[$pos]"

  companion object {

    /**
     * The dry sentinel.
     *
     * `NaN` rather than a very negative number, so that a caller who forgets to test it gets an arithmetic
     * result that is obviously wrong everywhere it is used, rather than a waterline at the bottom of the
     * world that looks like a rendering bug. The cost is that it must be tested with [hasWaterAt] and never
     * with `==`, since a NaN does not equal itself.
     */
    const val NO_WATER = Float.NaN

    fun index(i: Int, j: Int) = j * PatchGrid.SAMPLES + i
  }
}

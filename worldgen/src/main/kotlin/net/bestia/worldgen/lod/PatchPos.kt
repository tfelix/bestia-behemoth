package net.bestia.worldgen.lod

import net.bestia.worldgen.vector.Aabb

/**
 * The address of one coarse surface patch: a detail level and a position on that level's grid.
 *
 * Levels are anchored at the *fine* end, exactly as `TileId` anchors the map pyramid, and for the same
 * reason: level 0 is [PatchGrid.FINEST_METRES] per sample in every world that will ever exist, so a cached
 * patch, a client's disk copy and a tuning constant all keep meaning across a world resize. What a bigger
 * world changes is how many levels sit *above* the finest.
 *
 * [y] runs north with world y, not down like a screen row. The map tiles made the same choice, and the
 * reason is the same: a flip that lives in only one of the two producers is a bug that survives review,
 * because upside-down terrain still looks like terrain.
 */
data class PatchPos(val level: Int, val x: Int, val y: Int) {

  init {
    require(level in 0..PatchGrid.MAX_LEVEL) { "level must be 0..${PatchGrid.MAX_LEVEL}, was $level" }
  }

  val metresPerSample get() = PatchGrid.metresPerSample(level)

  /** Metres along one edge. */
  val span get() = PatchGrid.span(level)

  val bounds: Aabb
    get() = Aabb(x * span, y * span, (x + 1) * span, (y + 1) * span)

  /** World x of sample column [i], where `i == PatchGrid.CELLS` is the first sample of the next patch. */
  fun worldX(i: Int) = x * span + i * metresPerSample

  /** World y of sample row [j]. */
  fun worldY(j: Int) = y * span + j * metresPerSample

  override fun toString() = "L$level($x,$y)"
}

/**
 * How a detail level maps to a sample spacing and a footprint.
 *
 * ### Why the spacing doubles rather than quadruples
 *
 * Doubling makes every level's samples a strict subset of the level below it: a level-2 sample sits exactly
 * on a level-1 sample, which sits exactly on a level-0 one. That is what lets two adjacent patches at
 * different levels share an edge by *agreeing* on it rather than by interpolating towards it, and a seam
 * that is exact needs no stitching geometry at all.
 *
 * ### Why a patch is 64 cells and not a chunk
 *
 * The dominant cost of sampling is the feature query, not the heights: `ChunkHeightSampler`'s own KDoc notes
 * that asking it for one column costs the same as asking for the whole chunk. One query over a 256 m patch
 * therefore replaces sixty-four queries over the chunks inside it. Sixty-four cells also keeps a patch under
 * the sixteen-bit index a mesher wants and lands the finest level on exactly eight chunks.
 */
object PatchGrid {

  /** Sample spacing at level 0, in metres. Four is an eighth of a chunk edge. */
  const val FINEST_METRES = 4.0

  /** Cells along a patch edge. One more sample than this, because the far edge is shared with the neighbour. */
  const val CELLS = 64

  /** Samples along a patch edge, [CELLS] plus the shared far edge. */
  const val SAMPLES = CELLS + 1

  /**
   * Coarsest level offered: 32 m per sample over a 2 km patch.
   *
   * Not an arithmetic ceiling but a usefulness one. Thirty-two metres per sample is one chunk edge, which is
   * where `DetailRelief` already stops trusting the metre-scale detail field, and a 2 km patch is twice the
   * camera's far plane - so nothing coarser could be drawn even if it were sampled.
   */
  const val MAX_LEVEL = 3

  fun metresPerSample(level: Int) = FINEST_METRES * (1 shl level)

  fun span(level: Int) = CELLS * metresPerSample(level)

  /** The patch of [level] holding a world position. */
  fun at(level: Int, worldX: Double, worldY: Double): PatchPos {
    val span = span(level)
    return PatchPos(level, Math.floorDiv(Math.floor(worldX).toLong(), span.toLong()).toInt(),
      Math.floorDiv(Math.floor(worldY).toLong(), span.toLong()).toInt())
  }
}

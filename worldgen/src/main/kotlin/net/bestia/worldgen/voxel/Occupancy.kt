package net.bestia.worldgen.voxel

/**
 * How much of a voxel its material fills, quantised to a byte.
 *
 * ### Why a fraction and not a boolean
 *
 * The pipeline computes surface elevation as a continuous double, at full precision, and the entire vector
 * feature system exists to keep it that way - a river channel is carved to sub-metre accuracy against one
 * continuous polyline so that two chunks generated months apart agree about it. Quantising that to a solid
 * or empty voxel at the very last step throws the precision away and replaces it with metre stair-steps,
 * which is a strange thing to do after going to such lengths to preserve it.
 *
 * An occupancy fraction recovers it. A surface at 40.3 m is thirty percent of the voxel spanning 40 to 41,
 * and the client can reconstruct the original height to within a fifth of a centimetre. The information was
 * already there; storing it costs one byte per voxel, and that byte is 255 or 0 nearly everywhere, so it
 * costs almost nothing once encoded.
 *
 * [net.bestia.worldgen.derived.OpacityGrid] one level up is now built on exactly this number rather than
 * merely agreeing with it: a cell's occlusion is the occupancy accumulated through it, so a half-full voxel
 * stops half as much sight. It used to weight that by a per-material opacity as well - the fraction that let a
 * leaf canopy attenuate rather than either block outright or not at all - and leaves left the palette for
 * props, taking the only material that disagreed with its own solidity with them.
 */
object Occupancy {

  /** No material. The only legal occupancy for [BlockType.AIR]. */
  const val EMPTY = 0

  /** Completely filled. What every voxel below the air interface has. */
  const val FULL = 255

  /**
   * Quantises a fill fraction in `[0,1]`.
   *
   * A positive fraction never quantises to [EMPTY], even one far below the resolution of a byte. Zero means
   * air, and every derived structure relies on the topmost non-air voxel being the air interface; rounding a
   * two-millimetre sliver away would delete that interface and put a one-voxel hole in the ground instead.
   * A sliver is the honest answer and is invisible; a hole is neither.
   */
  fun of(fraction: Double): Int = when {
    fraction <= 0.0 -> EMPTY
    fraction >= 1.0 -> FULL
    else -> Math.round(fraction * FULL).toInt().coerceAtLeast(1)
  }

  /** The fraction [raw] stands for, in `[0,1]`. */
  fun fractionOf(raw: Int): Double = raw / FULL.toDouble()

  /** Reads an occupancy byte as an unsigned value. */
  fun unsigned(b: Byte): Int = b.toInt() and 0xFF

  fun byteOf(fraction: Double): Byte = of(fraction).toByte()

  val FULL_BYTE = FULL.toByte()
  val EMPTY_BYTE = EMPTY.toByte()
}

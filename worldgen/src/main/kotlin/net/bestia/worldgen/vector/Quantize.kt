package net.bestia.worldgen.vector

import kotlin.math.floor

/**
 * Turns a float into a discrete value before anything branches on it.
 *
 * Floats are fine for continuous fields: two nodes that disagree in the last bit about the height
 * of a hillside produce a hillside that differs by a nanometre, and nobody notices. Floats are not
 * fine for *decisions*. If one node decides a column is past a fjord sill and another decides it is
 * not, the two chunks disagree about whether there is rock there, and that is a visible seam or a
 * player falling through the world.
 *
 * The rule from worldgen-architecture.md: any branch on a float goes through a quantization step
 * first. This is where that step lives.
 */
object Quantize {

  /** Millimetre resolution. Far finer than any voxel, far coarser than double precision noise. */
  const val PER_METRE = 1000.0

  /** Fixed-point representation of [value] metres, rounded half-up. */
  fun toFixed(value: Double, unitsPerMetre: Double = PER_METRE): Long =
    floor(value * unitsPerMetre + 0.5).toLong()

  /** [value] snapped to the quantization grid. */
  fun snap(value: Double, unitsPerMetre: Double = PER_METRE): Double =
    toFixed(value, unitsPerMetre) / unitsPerMetre

  /** Order-stable comparison: equal whenever the two quantize to the same fixed-point value. */
  fun compare(a: Double, b: Double, unitsPerMetre: Double = PER_METRE): Int =
    toFixed(a, unitsPerMetre).compareTo(toFixed(b, unitsPerMetre))

  /** `a > b`, decided on the quantization grid so every node decides it the same way. */
  fun isAbove(a: Double, b: Double, unitsPerMetre: Double = PER_METRE): Boolean =
    compare(a, b, unitsPerMetre) > 0

  /** `a >= b`, decided on the quantization grid. */
  fun isAtLeast(a: Double, b: Double, unitsPerMetre: Double = PER_METRE): Boolean =
    compare(a, b, unitsPerMetre) >= 0
}

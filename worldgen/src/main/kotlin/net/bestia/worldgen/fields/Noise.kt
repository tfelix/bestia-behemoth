package net.bestia.worldgen.fields

import net.bestia.worldgen.core.GenRng
import kotlin.math.abs
import kotlin.math.floor

/**
 * Coherent noise, as a pure function of `(seed, position)`.
 *
 * There is no state, no permutation table to initialise, and no evaluation order: the lattice
 * gradients come straight out of the hash. A column therefore evaluates to the same value whether
 * it is reached from the chunk to its left or the chunk to its right, on this node or another one -
 * which is the property the base heightfield needs if vector features are going to blend against
 * something seam-free.
 *
 * Values are in `[-1, 1]` unless stated otherwise.
 */
object Noise {

  /** Gradient (Perlin-style) noise with a quintic fade, so the field is C2. */
  fun gradient2d(seed: Long, x: Double, y: Double): Double {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = x - x0
    val fy = y - y0

    val n00 = dotGradient(seed, x0, y0, fx, fy)
    val n10 = dotGradient(seed, x0 + 1, y0, fx - 1.0, fy)
    val n01 = dotGradient(seed, x0, y0 + 1, fx, fy - 1.0)
    val n11 = dotGradient(seed, x0 + 1, y0 + 1, fx - 1.0, fy - 1.0)

    val u = fade(fx)
    val v = fade(fy)

    val a = n00 + (n10 - n00) * u
    val b = n01 + (n11 - n01) * u

    // Gradient noise on a unit lattice peaks around 1/sqrt(2); scale so the range is usable as-is.
    return (a + (b - a) * v) * SQRT_2
  }

  /** Value noise: cheaper than [gradient2d], blockier, fine for masks and jitter. */
  fun value2d(seed: Long, x: Double, y: Double): Double {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = fade(x - x0)
    val fy = fade(y - y0)

    val v00 = lattice(seed, x0, y0)
    val v10 = lattice(seed, x0 + 1, y0)
    val v01 = lattice(seed, x0, y0 + 1)
    val v11 = lattice(seed, x0 + 1, y0 + 1)

    val a = v00 + (v10 - v00) * fx
    val b = v01 + (v11 - v01) * fx

    return a + (b - a) * fy
  }

  /**
   * Fractal Brownian motion: [octaves] octaves of [gradient2d], each [lacunarity] times finer and
   * [gain] times weaker. The normalisation keeps the result in `[-1, 1]` whatever the gain.
   */
  fun fbm(
    seed: Long,
    x: Double,
    y: Double,
    octaves: Int,
    frequency: Double = 1.0,
    lacunarity: Double = 2.0,
    gain: Double = 0.5
  ): Double {
    require(octaves >= 1) { "octaves must be at least 1, was $octaves" }

    var sum = 0.0
    var amplitude = 1.0
    var normalisation = 0.0
    var f = frequency

    for (octave in 0 until octaves) {
      // Each octave gets its own derived seed, so changing the octave count does not shift the
      // octaves that were already there.
      sum += gradient2d(GenRng.mix64(seed + octave), x * f, y * f) * amplitude
      normalisation += amplitude
      amplitude *= gain
      f *= lacunarity
    }

    return sum / normalisation
  }

  /**
   * Ridged multifractal: the sharp crests that read as mountain ranges rather than rolling hills.
   *
   * Returns `[0, 1]`, where 1 is a ridge line.
   */
  fun ridged(
    seed: Long,
    x: Double,
    y: Double,
    octaves: Int,
    frequency: Double = 1.0,
    lacunarity: Double = 2.0,
    gain: Double = 0.5
  ): Double {
    require(octaves >= 1) { "octaves must be at least 1, was $octaves" }

    var sum = 0.0
    var amplitude = 1.0
    var normalisation = 0.0
    var f = frequency

    for (octave in 0 until octaves) {
      val n = 1.0 - abs(gradient2d(GenRng.mix64(seed + octave), x * f, y * f))
      sum += n * n * amplitude
      normalisation += amplitude
      amplitude *= gain
      f *= lacunarity
    }

    return sum / normalisation
  }

  /**
   * Domain warping: offsets the sample position by a noise field before sampling.
   *
   * This is what takes the "same everywhere" look off a fractal field, and it is also how Voronoi
   * plate boundaries stop being visibly polygonal.
   *
   * @return the warped position
   */
  fun warp(
    seed: Long,
    x: Double,
    y: Double,
    amplitude: Double,
    frequency: Double = 1.0,
    octaves: Int = 2
  ): DoubleArray {
    val dx = fbm(GenRng.mix64(seed xor WARP_X_SALT), x, y, octaves, frequency)
    val dy = fbm(GenRng.mix64(seed xor WARP_Y_SALT), x, y, octaves, frequency)
    return doubleArrayOf(x + dx * amplitude, y + dy * amplitude)
  }

  private const val WARP_X_SALT = 0x5f3a1c9d2b7e4610L
  private const val WARP_Y_SALT = 0x21c0a3f7e5d18b46L
  private const val SQRT_2 = 1.4142135623730951

  /** Quintic fade; zero first and second derivative at both ends, so octaves do not show creases. */
  private fun fade(t: Double) = t * t * t * (t * (t * 6.0 - 15.0) + 10.0)

  /** Lattice value in `[-1, 1]`. */
  private fun lattice(seed: Long, x: Int, y: Int): Double =
    GenRng.hashUnit(seed, x.toLong(), y.toLong()) * 2.0 - 1.0

  private fun dotGradient(seed: Long, x: Int, y: Int, dx: Double, dy: Double): Double {
    // 16 evenly spaced directions: enough to avoid the axis-aligned artefacts of a 4- or 8-gradient
    // set, cheap enough to be a table lookup.
    val index = (GenRng.hash(seed, x.toLong(), y.toLong()) ushr 40).toInt() and 15
    return GRADIENT_X[index] * dx + GRADIENT_Y[index] * dy
  }

  private val GRADIENT_X = DoubleArray(16) { kotlin.math.cos(it * Math.PI / 8.0) }
  private val GRADIENT_Y = DoubleArray(16) { kotlin.math.sin(it * Math.PI / 8.0) }
}

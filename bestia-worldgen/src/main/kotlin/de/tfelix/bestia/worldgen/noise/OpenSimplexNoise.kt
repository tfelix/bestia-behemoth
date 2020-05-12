package de.tfelix.bestia.worldgen.noise

import kotlin.experimental.and

/*
 * OpenSimplex Noise in Java.
 * by Kurt Spencer
 *
 * v1.1 (October 5, 2014)
 * - Added 2D and 4D implementations.
 * - Proper gradient sets for all dimensions, from a
 *   dimensionally-generalizable scheme with an actual
 *   rhyme and reason behind it.
 * - Removed default permutation array in favor of
 *   default seed.
 * - Changed seed-based constructor to be independent
 *   of any particular randomization library, so results
 *   will be the same when ported to other languages.
 */
internal class OpenSimplexNoise {

  private val perm: ShortArray
  private val permGradIndex3D = ShortArray(256)

  constructor(perm: ShortArray) {
    this.perm = perm

    for (i in 0..255) {
      // Since 3D has 24 gradients, simple bitmask won't work, so
      // pre-compute modulo array.
      permGradIndex3D[i] = (perm[i] % (gradients3D.size / 3) * 3).toShort()
    }
  }

  // Initializes the class using a permutation array generated from a 64-bit
  // seed. Generates a proper permutation (i.e. doesn't merely perform N successive
  // pair swaps on a base array) Uses a simple 64-bit LCG.
  constructor(seed: Long = DEFAULT_SEED) {
    perm = ShortArray(256)
    val source = ShortArray(256)
    for (i in 0..255) {
      source[i] = i.toShort()
    }
    var mutableSeed = seed * 6364136223846793005L + 1442695040888963407L
    mutableSeed = mutableSeed * 6364136223846793005L + 1442695040888963407L
    mutableSeed = mutableSeed * 6364136223846793005L + 1442695040888963407L

    for (i in 255 downTo 0) {
      mutableSeed = mutableSeed * 6364136223846793005L + 1442695040888963407L
      var r = ((mutableSeed + 31) % (i + 1)).toInt()
      if (r < 0)
        r += i + 1
      perm[i] = source[r]
      permGradIndex3D[i] = (perm[i] % (gradients3D.size / 3) * 3).toShort()
      source[r] = source[i]
    }
  }

  // 2D OpenSimplex Noise.
  fun eval(x: Double, y: Double): Double {
    // Place input coordinates onto grid.
    val stretchOffset = (x + y) * STRETCH_CONSTANT_2D
    val xs = x + stretchOffset
    val ys = y + stretchOffset

    // Floor to get grid coordinates of rhombus (stretched square)
    // super-cell origin.
    var xsb = fastFloor(xs)
    var ysb = fastFloor(ys)

    // Skew out to get actual coordinates of rhombus origin. We'll need
    // these later.
    val squishOffset = (xsb + ysb) * SQUISH_CONSTANT_2D
    val xb = xsb + squishOffset
    val yb = ysb + squishOffset

    // Compute grid coordinates relative to rhombus origin.
    val xins = xs - xsb
    val yins = ys - ysb

    // Sum those together to get a value that determines which region we're
    // in.
    val inSum = xins + yins

    // Positions relative to origin point.
    var dx0 = x - xb
    var dy0 = y - yb

    // We'll be defining these inside the next block and using them
    // afterwards.
    val dx_ext: Double
    val dy_ext: Double
    val xsv_ext: Int
    val ysv_ext: Int

    var value = 0.0

    // Contribution (1,0)
    val dx1 = dx0 - 1 - SQUISH_CONSTANT_2D
    val dy1 = dy0 - 0 - SQUISH_CONSTANT_2D
    var attn1 = 2.0 - dx1 * dx1 - dy1 * dy1
    if (attn1 > 0) {
      attn1 *= attn1
      value += attn1 * attn1 * extrapolate(xsb + 1, ysb + 0, dx1, dy1)
    }

    // Contribution (0,1)
    val dx2 = dx0 - 0 - SQUISH_CONSTANT_2D
    val dy2 = dy0 - 1 - SQUISH_CONSTANT_2D
    var attn2 = 2.0 - dx2 * dx2 - dy2 * dy2
    if (attn2 > 0) {
      attn2 *= attn2
      value += attn2 * attn2 * extrapolate(xsb + 0, ysb + 1, dx2, dy2)
    }

    if (inSum <= 1) { // We're inside the triangle (2-Simplex) at (0,0)
      val zins = 1 - inSum
      if (zins > xins || zins > yins) { // (0,0) is one of the closest two
        // triangular vertices
        if (xins > yins) {
          xsv_ext = xsb + 1
          ysv_ext = ysb - 1
          dx_ext = dx0 - 1
          dy_ext = dy0 + 1
        } else {
          xsv_ext = xsb - 1
          ysv_ext = ysb + 1
          dx_ext = dx0 + 1
          dy_ext = dy0 - 1
        }
      } else { // (1,0) and (0,1) are the closest two vertices.
        xsv_ext = xsb + 1
        ysv_ext = ysb + 1
        dx_ext = dx0 - 1 - 2 * SQUISH_CONSTANT_2D
        dy_ext = dy0 - 1 - 2 * SQUISH_CONSTANT_2D
      }
    } else { // We're inside the triangle (2-Simplex) at (1,1)
      val zins = 2 - inSum
      if (zins < xins || zins < yins) { // (0,0) is one of the closest two
        // triangular vertices
        if (xins > yins) {
          xsv_ext = xsb + 2
          ysv_ext = ysb + 0
          dx_ext = dx0 - 2 - 2 * SQUISH_CONSTANT_2D
          dy_ext = dy0 + 0 - 2 * SQUISH_CONSTANT_2D
        } else {
          xsv_ext = xsb + 0
          ysv_ext = ysb + 2
          dx_ext = dx0 + 0 - 2 * SQUISH_CONSTANT_2D
          dy_ext = dy0 - 2 - 2 * SQUISH_CONSTANT_2D
        }
      } else { // (1,0) and (0,1) are the closest two vertices.
        dx_ext = dx0
        dy_ext = dy0
        xsv_ext = xsb
        ysv_ext = ysb
      }
      xsb += 1
      ysb += 1
      dx0 = dx0 - 1 - 2 * SQUISH_CONSTANT_2D
      dy0 = dy0 - 1 - 2 * SQUISH_CONSTANT_2D
    }

    // Contribution (0,0) or (1,1)
    var attn0 = 2.0 - dx0 * dx0 - dy0 * dy0
    if (attn0 > 0) {
      attn0 *= attn0
      value += attn0 * attn0 * extrapolate(xsb, ysb, dx0, dy0)
    }

    // Extra Vertex
    var attn_ext = 2.0 - dx_ext * dx_ext - dy_ext * dy_ext
    if (attn_ext > 0) {
      attn_ext *= attn_ext
      value += attn_ext * attn_ext * extrapolate(xsv_ext, ysv_ext, dx_ext, dy_ext)
    }

    return value / NORM_CONSTANT_2D
  }

  private fun extrapolate(xsb: Int, ysb: Int, dx: Double, dy: Double): Double {
    val index = perm[perm[xsb and 0xFF] + ysb and 0xFF] and 0x0E
    return gradients2D[index.toInt()] * dx + gradients2D[index + 1] * dy
  }

  private fun extrapolate(xsb: Int, ysb: Int, zsb: Int, dx: Double, dy: Double, dz: Double): Double {
    val index = permGradIndex3D[perm[perm[xsb and 0xFF] + ysb and 0xFF] + zsb and 0xFF].toInt()
    return (gradients3D[index] * dx
        + gradients3D[index + 1] * dy
        + gradients3D[index + 2] * dz)
  }

  private fun extrapolate(xsb: Int, ysb: Int, zsb: Int, wsb: Int, dx: Double, dy: Double, dz: Double, dw: Double): Double {
    val index = (perm[perm[perm[perm[xsb and 0xFF] + ysb and 0xFF] + zsb and 0xFF] + wsb and 0xFF] and 0xFC).toInt()
    return (gradients4D[index] * dx
        + gradients4D[index + 1] * dy
        + gradients4D[index + 2] * dz
        + gradients4D[index + 3] * dw)
  }

  companion object {
    private const val STRETCH_CONSTANT_2D = -0.211324865405187 // (1/Math.sqrt(2+1)-1)/2;
    private const val SQUISH_CONSTANT_2D = 0.366025403784439 // (Math.sqrt(2+1)-1)/2;
    private const val STRETCH_CONSTANT_3D = -1.0 / 6 // (1/Math.sqrt(3+1)-1)/3;
    private const val SQUISH_CONSTANT_3D = 1.0 / 3 // (Math.sqrt(3+1)-1)/3;
    private const val STRETCH_CONSTANT_4D = -0.138196601125011 // (1/Math.sqrt(4+1)-1)/4;
    private const val SQUISH_CONSTANT_4D = 0.309016994374947 // (Math.sqrt(4+1)-1)/4;

    private const val NORM_CONSTANT_2D = 47.0
    private const val NORM_CONSTANT_3D = 103.0
    private const val NORM_CONSTANT_4D = 30.0

    private const val DEFAULT_SEED: Long = 0

    private fun fastFloor(x: Double): Int {
      val xi = x.toInt()
      return if (x < xi) xi - 1 else xi
    }

    //Gradients for 2D. They approximate the directions to the
    //vertices of an octagon from the center.
    private val gradients2D = byteArrayOf(
         5,  2,   2,  5,
        -5,  2,  -2,  5,
         5, -2,   2, -5,
        -5, -2,  -2, -5)

    // Gradients for 3D. They approximate the directions to the
    // vertices of a rhombicuboctahedron from the center, skewed so
    // that the triangular and square facets can be inscribed inside
    // circles of the same radius.
    private val gradients3D = byteArrayOf(
        -11, 4, 4,   -4, 11, 4,   -4, 4, 11,
        11, 4, 4,     4, 11, 4,    4, 4, 11,
        -11, -4, 4,  -4, -11, 4,  -4, -4, 11,
        11, -4, 4,   4, -11, 4,    4, -4, 11,
        -11, 4, -4,  -4, 11, -4,  -4, 4, -11,
        11, 4, -4,   4, 11, -4,   4, 4, -11,
        -11, -4, -4,  -4, -11,   -4, -4, -4,
        -11, 11, -4,  -4, 4, -11, -4, 4, -4, -11)

    // Gradients for 4D. They approximate the directions to the
    // vertices of a disprismatotesseractihexadecachoron from the center,
    // skewed so that the tetrahedral and cubic facets can be inscribed inside
    // spheres of the same radius.
    private val gradients4D = byteArrayOf(
        3, 1, 1, 1,   1, 3, 1, 1,   1, 1, 3, 1,
        1, 1, 1, 3,  -3, 1, 1, 1,  -1, 3, 1, 1,
        -1, 1, 3, 1, -1, 1, 1, 3,   3, -1, 1, 1,
        1, -3, 1, 1,  1, -1, 3, 1,  1, -1, 1, 3,
        -3, -1, 1, 1, -1, -3, 1, 1, -1, -1, 3, 1,
        -1, -1, 1, 3, 3, 1, -1, 1, 1, 3, -1, 1,
        1, 1, -3, 1, 1, 1, -1, 3, -3, 1, -1, 1,
        -1, 3, -1, 1, -1, 1, -3, 1, -1, 1, -1, 3,
        3, -1, -1, 1, 1, -3, -1, 1, 1, -1, -3, 1,
        1, -1, -1, 3, -3, -1, -1, 1, -1, -3, -1, 1,
        -1, -1, -3, 1, -1, -1, -1, 3, 3, 1, 1, -1,
        1, 3, 1, -1, 1, 1, 3, -1, 1, 1, 1, -3, -3,
        1, 1, -1, -1, 3, 1, -1, -1, 1, 3, -1, -1,
        1, 1, -3, 3, -1, 1, -1, 1, -3, 1, -1, 1, -1,
        3, -1, 1, -1, 1, -3, -3, -1, 1, -1, -1, -3,
        1, -1, -1, -1, 3, -1, -1, -1, 1, -3, 3, 1,
        -1, -1, 1, 3, -1, -1, 1, 1, -3, -1, 1, 1, -1,
        -3, -3, 1, -1, -1, -1, 3, -1, -1, -1, 1, -3,
        -1, -1, 1, -1, -3, 3, -1, -1, -1, 1, -3, -1,
        -1, 1, -1, -3, -1, 1, -1, -1, -3, -3, -1, -1,
        -1, -1, -3, -1, -1, -1, -1, -3, -1, -1, -1, -1,
        -3
    )
  }
}

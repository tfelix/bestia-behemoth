package net.bestia.worldgen.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lambertian relief shading over an already-sampled height buffer.
 *
 * Worth the code: a colour ramp alone hides everything that matters about terrain. A ramp shows a
 * plateau and a gently domed plain as the same green; relief shading separates them instantly, and
 * it is the only way to see whether an erosion stage produced drainage that looks like drainage or
 * like noise with a gradient applied to it.
 *
 * Gradients are taken in *pixel* space and converted with the current scale, so shading stays
 * meaningful at every zoom instead of vanishing when the view is wide.
 */
object Hillshade {

  /**
   * @param heights row-major, `width * height`, NaN where there is no value
   * @param exaggeration vertical multiplier; 1 is physically honest and almost always too flat to
   *   read at world scale
   * @return per-pixel brightness multipliers around 1.0
   */
  fun shade(
    heights: DoubleArray,
    width: Int,
    height: Int,
    metresPerPixel: Double,
    exaggeration: Double = 2.0,
    azimuthDegrees: Double = 315.0,
    altitudeDegrees: Double = 45.0,
    ambient: Double = 0.45
  ): DoubleArray {
    require(heights.size == width * height) {
      "Height buffer is ${heights.size}, expected ${width * height}"
    }

    val azimuth = Math.toRadians(azimuthDegrees)
    val altitude = Math.toRadians(altitudeDegrees)

    // Light direction in world space, with screen y pointing north.
    val lightX = cos(altitude) * sin(azimuth)
    val lightY = cos(altitude) * cos(azimuth)
    val lightZ = sin(altitude)

    val out = DoubleArray(width * height)
    val step = 2.0 * metresPerPixel / exaggeration

    for (y in 0 until height) {
      for (x in 0 until width) {
        val i = y * width + x
        val centre = heights[i]
        if (centre.isNaN()) {
          out[i] = 1.0
          continue
        }

        val left = sampleOr(heights, width, height, x - 1, y, centre)
        val right = sampleOr(heights, width, height, x + 1, y, centre)
        val up = sampleOr(heights, width, height, x, y - 1, centre)
        val down = sampleOr(heights, width, height, x, y + 1, centre)

        // Screen rows run north to south, so the north-south gradient is (up - down).
        val dzdx = (right - left) / step
        val dzdy = (up - down) / step

        // Surface normal of the height field: (-dz/dx, -dz/dy, 1), normalised.
        val inverseLength = 1.0 / sqrt(dzdx * dzdx + dzdy * dzdy + 1.0)
        val lambert = (-dzdx * lightX - dzdy * lightY + lightZ) * inverseLength

        // Normalised against flat ground, so level terrain comes out at exactly its palette colour
        // and only actual relief changes the brightness. Without this every shaded map is tinted
        // by whatever light altitude happened to be chosen.
        val relative = if (lightZ > 0.0) lambert / lightZ else lambert

        out[i] = (ambient + (1.0 - ambient) * relative).coerceIn(ambient, MAX_BRIGHTNESS)
      }
    }

    return out
  }

  /** Slopes facing the light brighten, but not to the point of clipping the palette to white. */
  private const val MAX_BRIGHTNESS = 1.6

  /** Edge and hole handling: fall back to the centre value, which shades those pixels flat. */
  private fun sampleOr(
    heights: DoubleArray,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
    fallback: Double
  ): Double {
    if (x < 0 || y < 0 || x >= width || y >= height) return fallback
    val v = heights[y * width + x]
    return if (v.isNaN()) fallback else v
  }
}

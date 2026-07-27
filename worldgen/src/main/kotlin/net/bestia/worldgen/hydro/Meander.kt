package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.PolylineFeature
import kotlin.math.min

/**
 * Sinuosity applied to a river centerline, as a pure function of arc length.
 *
 * Meandering has to happen here - at vector level, on the one continuous centerline - and not at chunk
 * level. This is the single most important consequence of the three-representation split. A chunk that
 * perturbed the channel with its own seed could not possibly agree with its neighbour about where the
 * channel is, so the river would visibly jump sideways at every chunk border. Offsetting the shared
 * centerline once means every chunk that samples it agrees by construction.
 */
object Meander {

  /**
   * Lateral offset in metres at arc length [s].
   *
   * Two octaves of one-dimensional noise: the first sets the meander wavelength, the second adds the
   * irregularity that stops it reading as a sine wave. Both are sampled from a two-dimensional field
   * along a line, which is a cheap way to get 1D noise without a second implementation.
   *
   * @param taperLength metres at each end over which the offset fades to zero. **Not optional.** A
   *   reach whose ends move no longer meets the reach below it, and the river acquires a step at every
   *   confluence - which looks exactly like the chunk-seam bug this whole design exists to avoid, and
   *   would send you looking in the wrong place.
   */
  fun offset(
    seed: Long,
    s: Double,
    length: Double,
    amplitude: Double,
    wavelength: Double,
    taperLength: Double
  ): Double {
    if (amplitude <= 0.0 || wavelength <= 0.0) return 0.0

    val primary = Noise.gradient2d(seed, s / wavelength, PHASE)
    val secondary = Noise.gradient2d(seed + 1L, s / (wavelength * SECONDARY_FACTOR), PHASE)

    return (primary + secondary * SECONDARY_WEIGHT) / (1.0 + SECONDARY_WEIGHT) *
        amplitude * taper(s, length, taperLength)
  }

  /** 0 at both ends, 1 in the middle, with zero slope where it meets zero. */
  private fun taper(s: Double, length: Double, taperLength: Double): Double {
    if (taperLength <= 0.0) return 1.0
    val fromEnd = min(s, length - s)
    if (fromEnd >= taperLength) return 1.0
    if (fromEnd <= 0.0) return 0.0
    return PolylineFeature.smoothstep(fromEnd / taperLength)
  }

  /**
   * Meander amplitude for a channel of a given width on a given slope.
   *
   * Amplitude grows with width - a big river swings further - and collapses on steep ground, because a
   * mountain stream is confined by the valley it has cut and has nowhere to wander. That second term is
   * what makes headwaters straight and lowland trunks sinuous, which is the difference between a river
   * network that reads as a landscape and one that reads as noise applied to lines.
   */
  fun amplitudeFor(width: Double, slope: Double, widthFactor: Double, cap: Double): Double {
    val confinement = 1.0 / (1.0 + slope * SLOPE_CONFINEMENT)
    return min(width * widthFactor * confinement, cap)
  }

  /** Arbitrary but fixed second coordinate, so the 2D field reads as one dimensional. */
  private const val PHASE = 0.37

  private const val SECONDARY_FACTOR = 0.41
  private const val SECONDARY_WEIGHT = 0.45

  /** How strongly slope suppresses wandering. At a slope of 0.02 the amplitude is already halved. */
  private const val SLOPE_CONFINEMENT = 50.0
}

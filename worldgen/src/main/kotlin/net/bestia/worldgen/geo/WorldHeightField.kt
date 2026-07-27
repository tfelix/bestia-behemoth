package net.bestia.worldgen.geo

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.PolylineFeature
import kotlin.math.sqrt

/** Tuning for [WorldHeightField]. */
data class DetailParams(

  /** Wavelength of the broad detail in metres. */
  val wavelength: Double = 340.0,

  /** Amplitude of the broad detail in metres, on the roughest ground. */
  val amplitude: Double = 15.0,

  /** Wavelength of the ridge detail - the spurs and gullies of a mountainside. */
  val ridgeWavelength: Double = 760.0,

  val ridgeAmplitude: Double = 24.0,

  /** Slope at which ridge detail reaches full strength. Below it the ground is too gentle for spurs. */
  val ridgeSlope: Double = 0.14,

  /** Fraction of the detail amplitude that survives on the deep sea floor. */
  val marineDamping: Double = 0.35
)

/**
 * The continuous base heightfield the chunk tier lifts: the coarse raster, bicubically interpolated,
 * plus terrain detail below the resolution the raster can hold.
 *
 * Two properties are non-negotiable here, and both are about seams.
 *
 * It must be a **pure function of world position**. Vector features guarantee their own continuity, but
 * they blend against this - if this has a seam, the river floor is continuous and its banks are not,
 * which is a far more confusing bug than a discontinuous river.
 *
 * It must be **the same function on every node**. That is why the detail is analytic noise rather than
 * the particle droplet erosion the architecture document asks for at this point. Droplet erosion is
 * stateful and non-local: it needs the chunk generated with an overlap margin and blended in the
 * overlap, and any error in that blend reintroduces exactly the seams the vector tier was built to
 * eliminate. The trade is real and visible - analytic detail has no sediment transport, so it gives
 * gullies without the debris fans at the bottom of them - and it is recorded as a known deviation
 * rather than presented as equivalent.
 */
class WorldHeightField(
  private val elevation: FloatLayer,
  private val hardness: FloatLayer,
  seed: Long,
  private val seaLevel: Double = 0.0,
  private val params: DetailParams = DetailParams()
) : BaseHeightField {

  private val detailSeed = GenRng.mix64(seed xor DETAIL_SALT)
  private val ridgeSeed = GenRng.mix64(seed xor RIDGE_SALT)

  /** Half a cell: the shortest baseline over which the coarse raster carries any slope at all. */
  private val slopeStep = elevation.region.resolution.metresPerCell * 0.5

  override fun heightAt(worldX: Double, worldY: Double): Double {
    val coarse = elevation.sampleBicubic(worldX, worldY)
    val slope = coarseSlopeAt(worldX, worldY)
    val rock = hardness.sampleBilinear(worldX, worldY).coerceIn(0.0, 1.0)

    // Rough where it is steep, and rougher on hard rock: soft rock slumps into smooth convex hills,
    // hard rock breaks into angular ground. A single amplitude everywhere is the thing that makes an
    // otherwise good heightfield read as noise laid over a map.
    val roughness = (0.22 + slope * 5.5).coerceIn(0.22, 1.0) * (0.55 + 0.45 * rock)

    // A smooth ramp rather than a depth test, so the sea floor damping cannot introduce a step. Any
    // branch here would be a branch on a float that two nodes must agree on, which the architecture
    // document requires quantising first - avoiding the branch avoids the question.
    val marine = PolylineFeature.smoothstep((seaLevel - coarse) / MARINE_RAMP)
    val damping = 1.0 - (1.0 - params.marineDamping) * marine

    var z = coarse

    z += Noise.fbm(
      detailSeed, worldX / params.wavelength, worldY / params.wavelength, DETAIL_OCTAVES
    ) * params.amplitude * roughness * damping

    // Ridge detail only on ground steep enough to have spurs, faded in so its edge is invisible.
    val ridgeMask = PolylineFeature.smoothstep(slope / params.ridgeSlope)
    if (ridgeMask > 0.0) {
      val ridge = Noise.ridged(
        ridgeSeed, worldX / params.ridgeWavelength, worldY / params.ridgeWavelength, RIDGE_OCTAVES
      )
      // Subtract the field's mean so turning the ridge amplitude up does not also raise the mountain.
      z += (ridge - RIDGED_MEAN) * params.ridgeAmplitude * ridgeMask * damping
    }

    return z
  }

  /**
   * Slope of the coarse raster, by central differences over half a cell.
   *
   * Central rather than forward differences: a forward difference is biased in the direction it looks,
   * and the bias shows up as detail being systematically rougher on one side of every ridge.
   */
  fun coarseSlopeAt(worldX: Double, worldY: Double): Double {
    val dzdx = (elevation.sampleBicubic(worldX + slopeStep, worldY) -
        elevation.sampleBicubic(worldX - slopeStep, worldY)) / (2.0 * slopeStep)
    val dzdy = (elevation.sampleBicubic(worldX, worldY + slopeStep) -
        elevation.sampleBicubic(worldX, worldY - slopeStep)) / (2.0 * slopeStep)
    return sqrt(dzdx * dzdx + dzdy * dzdy)
  }

  private companion object {
    const val DETAIL_SALT = 0x4C1E7B93A25D608L
    const val RIDGE_SALT = 0x7A3B19C5D2E4L

    const val DETAIL_OCTAVES = 5
    const val RIDGE_OCTAVES = 4

    /** Mean of [Noise.ridged] at the default gain. */
    const val RIDGED_MEAN = 0.33

    /** Metres below sea level over which detail fades to its marine amplitude. */
    const val MARINE_RAMP = 220.0
  }
}

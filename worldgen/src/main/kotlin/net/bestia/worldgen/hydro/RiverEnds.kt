package net.bestia.worldgen.hydro

import net.bestia.worldgen.core.GenRng
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How a river's two ends depart from the width its discharge alone would give it: a narrower floodplain and a
 * steady channel at the source, and a mouth that opens out where it meets the sea.
 *
 * Both are deliberately *shape* rather than hydraulics. [ChannelGauge] decides how much water a channel carries
 * and how wide that makes it; these decide what a player reads at either end of it - that a river began as
 * something small and ended as something wide. Neither one can move a channel below the gauge's floor, for the
 * floor's own reason: under three voxels of wetted width the water flickers in and out along the reach.
 */
internal object RiverEnds {

  /**
   * The mouth one sea-ending reach was given. Every reach draws its own, so no two estuaries are alike.
   *
   * @param length metres upstream of the mouth over which the channel opens out.
   * @param flare width at the mouth over width without the flare. One is no flare at all.
   * @param exponent how late the opening happens. Above one the river holds its width and opens in the last
   *   stretch; below one it widens steadily the whole way down.
   */
  class Mouth(val length: Double, val flare: Double, val exponent: Double) {

    /** Width multiplier at [fromMouth] metres upstream of the mouth. */
    fun factorAt(fromMouth: Double): Double {
      if (length <= 0.0 || flare <= 1.0 || fromMouth >= length) return 1.0
      val t = (1.0 - fromMouth.coerceAtLeast(0.0) / length)
      val eased = t * t * (3.0 - 2.0 * t)
      return 1.0 + (flare - 1.0) * eased.pow(exponent)
    }

    companion object {
      val NONE = Mouth(0.0, 1.0, 1.0)
    }
  }

  /**
   * Draws the mouth of one reach.
   *
   * The draw is skewed rather than uniform, so most rivers open modestly and a few open into a broad funnel, and
   * the skew is leaned on by the ground. A steep run to the sea and a large discharge lean towards a wide
   * estuary, which is a drowned valley the tide scours. A flat run leans the other way, because a flat mouth is
   * where the river drops its load and builds a delta instead (`AlluviumStage`), and a delta and a broad funnel
   * are alternatives. It is a lean and not a rule, so either can still happen anywhere.
   *
   * @param mouthGradient fall per metre over the last few kilometres of the reach, from the coarse filled
   *   surface - the bed is not known yet when the width is decided, since the bed is probed across a corridor
   *   as wide as that width.
   * @param discharge at the mouth, in cubic metres a second.
   * @param referenceDischarge a typical trunk's discharge in this world, so the discharge lean is relative.
   */
  fun mouthFor(
    seed: Long,
    reachId: Int,
    widthAtMouth: Double,
    reachLength: Double,
    mouthGradient: Double,
    discharge: Double,
    referenceDischarge: Double,
    params: HydrologyParams
  ): Mouth {
    if (params.estuaryFlareMax <= 1.0) return Mouth.NONE

    val h = GenRng.hash(seed, reachId.toLong(), ESTUARY_SALT)
    val lengthRoll = GenRng.unit(h)
    val flareRoll = GenRng.unit(GenRng.mix64(h + 1))
    val shapeRoll = GenRng.unit(GenRng.mix64(h + 2))

    // Ground lean in roughly [-1, 1]: positive towards a wide funnel.
    val steepness = ((mouthGradient / params.estuaryReferenceGradient) - 1.0).coerceIn(-1.0, 1.0)
    val size = if (referenceDischarge > 0.0) {
      ((discharge / referenceDischarge) - 1.0).coerceIn(-1.0, 1.0)
    } else {
      0.0
    }
    val lean = 0.6 * steepness + 0.4 * size

    // A power on a uniform roll: a large skew keeps nearly every draw near the bottom of the range, and the lean
    // moves the skew rather than the range, so the extremes stay reachable from any ground.
    val skew = params.estuarySkew * exp(-lean)
    val flare = params.estuaryFlareMin + (params.estuaryFlareMax - params.estuaryFlareMin) * flareRoll.pow(skew)

    val factor = params.estuaryLengthFactorMin +
        (params.estuaryLengthFactorMax - params.estuaryLengthFactorMin) * lengthRoll
    val length = min(
      (widthAtMouth * flare * factor).coerceIn(params.estuaryMinLength, params.estuaryMaxLength),
      reachLength * MAX_REACH_SHARE
    )

    // 0.5 (opens all the way down) to 2.5 (holds its width and opens in the last stretch).
    val exponent = 0.5 + 2.0 * shapeRoll

    return Mouth(length, max(1.0, flare), exponent)
  }

  /**
   * How far along the source taper a station is, from zero at the head to one past [taperLength].
   *
   * Used to hold a headwater at the gauge's floor - no pool-riffle opening out - and to narrow its floodplain,
   * so the first kilometres read as a stream in a tight valley rather than as the river it becomes.
   */
  fun sourceProgress(s: Double, taperLength: Double): Double {
    if (taperLength <= 0.0) return 1.0
    val t = (s / taperLength).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
  }

  /** At most this share of a reach opens out, so a short coastal reach is not all funnel. */
  private const val MAX_REACH_SHARE = 0.5

  /** Its own salt, so the mouth does not draw the meander's, the bank wobble's or the riffle's numbers. */
  private const val ESTUARY_SALT = 0x45535455L
}

package net.bestia.worldgen.climate

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The wind belts, and specifically the one property the seasonal shift can break.
 *
 * `Winds.eastwardShare` blends the trade/westerly and westerly/polar boundaries over `BELT_TRANSITION` degrees
 * each. Its own KDoc warns that the transition has to stay clear of the seasonal shift plus the gap between the
 * two limits, "or the trade and polar transitions start eating each other" - and moving to four seasons is
 * exactly the change that would quietly invalidate that, because it changes how the shift is computed. A belt
 * boundary that stops blending is a step function drawn across every continent, which is the artefact the
 * blend was introduced to remove.
 */
class WindsTest {

  private val params = ClimateParams()

  /** Every belt shift the four-season cycle actually asks for. */
  private fun shifts(): List<Double> {
    val lag = 2.0 * PI * params.monsoonLagMonths / ClimateStage.MONTHS_PER_YEAR
    return (0 until params.seasons).map {
      params.seasonalShift * sin(2.0 * PI * it / params.seasons - lag)
    }
  }

  @Test
  fun `the seasonal shift never exceeds its configured amplitude`() {
    // A sine is bounded by its amplitude, so this is really a guard on the phase arithmetic: an exponent or a
    // stray factor of two here would widen the migration and start eating the belt transitions below.
    for (shift in shifts()) {
      assertTrue(
        abs(shift) <= params.seasonalShift + 1e-9,
        "belt shift ${"%.3f".format(Locale.ROOT, shift)} exceeds seasonalShift ${params.seasonalShift}"
      )
    }
  }

  @Test
  fun `the belt transitions stay separated at every seasonal shift`() {
    // The share is a product of two smoothsteps, one entering the westerlies and one entering the polar cell.
    // If they overlap there is no latitude that is purely westerly, and the westerly belt - which is most of
    // the temperate world - stops existing as a belt. Sampled rather than reasoned: the shift enters through
    // `abs(latitude) - shift * hemisphere`, and reproducing that here would be reproducing the bug.
    for (shift in shifts()) {
      var peak = 0.0
      var at = 0.0
      var latitude = -90.0
      while (latitude <= 90.0) {
        val share = Winds.eastwardShare(latitude, shift)
        if (share > peak) {
          peak = share
          at = latitude
        }
        latitude += 0.25
      }

      assertTrue(
        peak > 0.97,
        "at shift ${"%.2f".format(Locale.ROOT, shift)} the westerlies peak at only " +
            "${"%.3f".format(Locale.ROOT, peak)} (latitude $at) - the trade and polar transitions overlap"
      )
    }
  }

  @Test
  fun `the eastward share is continuous in latitude`() {
    // The defect this function exists to fix was a step function, and the mixing blur cannot repair a jump.
    // A cap on the step between adjacent samples is the direct statement of that.
    for (shift in shifts()) {
      var previous = Winds.eastwardShare(-90.0, shift)
      var latitude = -90.0 + 0.25
      while (latitude <= 90.0) {
        val share = Winds.eastwardShare(latitude, shift)
        assertTrue(
          abs(share - previous) < 0.10,
          "eastwardShare jumps by ${abs(share - previous)} at latitude $latitude, shift $shift"
        )
        previous = share
        latitude += 0.25
      }
    }
  }

  @Test
  fun `the hemispheres shift oppositely`() {
    // A positive shift moves the belts poleward in the north and equatorward in the south, which is what makes
    // one scalar enough to describe the whole world's season. If this stopped holding, both hemispheres would
    // have summer at once.
    val shift = params.seasonalShift

    val northMoved = Winds.eastwardShare(35.0, shift) - Winds.eastwardShare(35.0, 0.0)
    val southMoved = Winds.eastwardShare(-35.0, shift) - Winds.eastwardShare(-35.0, 0.0)

    assertTrue(
      northMoved * southMoved < 0.0,
      "the same shift moved both hemispheres the same way ($northMoved, $southMoved)"
    )
  }
}

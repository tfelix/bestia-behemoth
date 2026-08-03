package net.bestia.worldgen.climate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * The calendar is the one part of this system that is wrong *silently*: every mapping below has a plausible
 * off-by-one that produces a world whose seasons are merely shifted, which no rendering and no invariant can
 * tell from a world whose seasons are right.
 */
class SeasonsTest {

  @Test
  fun `a quarter centre's orbital phase is the phase the climate stage samples at`() {
    // The whole reason Seasons exists: the runtime's continuous curve must pass through the four points the
    // generator actually sampled, not merely through four points of the same shape.
    for (quarter in 0 until Seasons.QUARTERS) {
      val fromProgress = Seasons.orbitalPhase(Seasons.quarterCentreProgress(quarter))
      val fromIndex = Seasons.phaseOfQuarter(quarter)

      assertEquals(fromIndex, fromProgress, 1e-12, "quarter $quarter")
    }
  }

  @Test
  fun `the quarter centres are an eighth, three eighths, five eighths and seven eighths`() {
    assertEquals(0.125, Seasons.quarterCentreProgress(0), 1e-12)
    assertEquals(0.375, Seasons.quarterCentreProgress(1), 1e-12)
    assertEquals(0.625, Seasons.quarterCentreProgress(2), 1e-12)
    assertEquals(0.875, Seasons.quarterCentreProgress(3), 1e-12)
  }

  @Test
  fun `the extracted expressions are the ones the climate stage used, bit for bit`() {
    // Substituting these two calls into ClimateStage.seasonalPrecipitation must not move a single value in a
    // single layer, or the refactor silently regenerated every world. `assertEquals` on Double with no
    // tolerance is the assertion that says so; a delta here would let the refactor be merely close.
    val tau = 2.0 * Math.PI

    for (quarter in 0 until Seasons.QUARTERS) {
      val phase = tau * quarter / Seasons.QUARTERS

      assertEquals(phase, Seasons.phaseOfQuarter(quarter, Seasons.QUARTERS))
      assertEquals(0.5 * sin(phase), Seasons.warmingAtPhase(phase))
    }
  }

  @Test
  fun `the northern hemisphere is warmest in the summer quarter and coldest in winter`() {
    val spring = Seasons.northernWarming(Seasons.quarterCentreProgress(0))
    val summer = Seasons.northernWarming(Seasons.quarterCentreProgress(1))
    val autumn = Seasons.northernWarming(Seasons.quarterCentreProgress(2))
    val winter = Seasons.northernWarming(Seasons.quarterCentreProgress(3))

    assertEquals(0.0, spring, 1e-12)
    assertEquals(0.5, summer, 1e-12)
    assertEquals(0.0, autumn, 1e-12)
    assertEquals(-0.5, winter, 1e-12)
  }

  @Test
  fun `the year has one peak and one trough, not two`() {
    // The defect the reordered Season enum was fixing: a calendar running summer, winter, fall, spring needs
    // two warm and two cold periods a year, which a single sine cannot express at any tuning.
    var risingRuns = 0
    var previous = Seasons.northernWarming(0.0)
    var wasRising = true

    for (step in 1..1000) {
      val current = Seasons.northernWarming(step / 1000.0)
      val rising = current > previous
      if (rising != wasRising) risingRuns++
      wasRising = rising
      previous = current
    }

    assertEquals(2, risingRuns, "a single annual cycle turns exactly twice")
  }

  @Test
  fun `the hemispheres are opposed at every instant`() {
    for (step in 0 until 100) {
      val progress = step / 100.0
      val north = Seasons.warmingAt(progress, northwards = 0.8)
      val south = Seasons.warmingAt(progress, northwards = 0.2)

      assertEquals(north, -south, 1e-12, "at yearProgress $progress")
    }
  }

  @Test
  fun `the hemisphere flip does not depend on where the pole is`() {
    // hemisphereSign takes `northwards` rather than a latitude precisely so a caller without the configured
    // polewardLatitude cannot get it wrong. Scaling a ramp through the origin cannot move its zero.
    for (poleward in listOf(20.0, 45.0, 68.0, 89.0)) {
      for (step in 0..20) {
        val northwards = step / 20.0
        val latitude = ClimateStage.latitudeOf(northwards, poleward)

        assertEquals(
          latitude >= 0.0,
          Seasons.hemisphereSign(northwards) >= 0.0,
          "northwards $northwards at poleward $poleward"
        )
      }
    }
  }

  @Test
  fun `climateMonthOf lands the quarter centres on the months SeasonalPrecipitation samples`() {
    // atMonth treats each layer as the value at its quarter's centre: months 1.5, 4.5, 7.5, 10.5 of a
    // twelve-month year. Handing it a four-month runtime month instead reads a third of the way in.
    val expected = listOf(1.5, 4.5, 7.5, 10.5)

    for (quarter in 0 until Seasons.QUARTERS) {
      val month = Seasons.climateMonthOf(Seasons.quarterCentreProgress(quarter))

      assertEquals(expected[quarter], month, 1e-12, "quarter $quarter")
    }
  }

  @Test
  fun `quarterOf splits the year at the boundaries, not at the centres`() {
    assertEquals(0, Seasons.quarterOf(0.0))
    assertEquals(0, Seasons.quarterOf(0.24))
    assertEquals(1, Seasons.quarterOf(0.25))
    assertEquals(3, Seasons.quarterOf(0.99))
  }

  @Test
  fun `quarterOf wraps rather than throwing past the end of the year`() {
    assertEquals(0, Seasons.quarterOf(1.0))
    assertEquals(1, Seasons.quarterOf(1.3))
    assertEquals(3, Seasons.quarterOf(-0.1))
  }

  @Test
  fun `orbitalPhase is periodic over a year`() {
    for (step in 0..20) {
      val progress = step / 20.0
      val here = Seasons.northernWarming(progress)
      val nextYear = Seasons.northernWarming(progress + 1.0)

      assertTrue(abs(here - nextYear) < 1e-12, "at yearProgress $progress")
    }
  }
}

package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.environment.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which clock each drive is on, and what that buys.
 *
 * Hunger and tiredness are authored per in-game hour: a day here is eight real hours, so a rate stated per
 * real second reads as a mistake beside a townsperson who eats at noon. Restlessness is the other kind - it
 * decides how long a creature stands about where a player can see it, so it is stated per real second and a
 * retune of `speed-factor` must not touch it.
 */
class DriveRateConversionTest {

  @Test
  fun `the bestia appetites move at the rates they always did`() {
    assertEquals(0.55f, perRealSecond(BestiaDomain.HUNGER), TOLERANCE, "hunger")
    assertEquals(0.25f, perRealSecond(BestiaDomain.TIREDNESS), TOLERANCE, "tiredness")
  }

  @Test
  fun `tiredness still recovers twenty times faster than it accumulates`() {
    val tiredness = driveFor(BestiaDomain.TIREDNESS)

    assertEquals(-5.0f, asleepPerRealSecond(tiredness), TOLERANCE, "sleeping recovery")
    assertEquals(
      -20.0f,
      tiredness.whileSleepingRate / tiredness.rate,
      TOLERANCE,
      "a full night has to be slept off well before dawn"
    )
  }

  @Test
  fun `nothing else changes while asleep`() {
    for (key in listOf(BestiaDomain.HUNGER, BestiaDomain.RESTLESSNESS)) {
      val drive = driveFor(key)
      assertEquals(
        drive.rate,
        drive.whileSleepingRate,
        "lying down does not feed you and it certainly does not make you less bored"
      )
    }
  }

  /**
   * The gap between one roam and the next, which is what a player actually watches.
   *
   * Counted in whole sweeps because `AiDriveSystem` runs once a second, so that is the resolution the
   * threshold is really crossed at.
   */
  @Test
  fun `a creature is restless again a few seconds after a roam spent it`() {
    val sweeps = sweepsToReach(driveFor(BestiaDomain.RESTLESSNESS), BestiaDomain.DEFAULT_RESTLESS_THRESHOLD)

    assertTrue(sweeps in 3..4, "a creature stood about for $sweeps seconds between roams")
  }

  @Test
  fun `a townsperson is restless again a few seconds after loitering settled it`() {
    val restlessness = TownsfolkDomain.DRIVES.first { it.key == TownsfolkDomain.RESTLESSNESS }
    // The floor goal is skipped while its desired state holds, so the wait is however long it takes to get
    // back *past* settled rather than to it.
    val sweeps = sweepsToReach(restlessness, TownsfolkDomain.SETTLED_RESTLESSNESS + 1)

    assertTrue(sweeps in 3..4, "a townsperson stood about for $sweeps seconds between bouts of loitering")
  }

  @Test
  fun `retuning the world clock does not make creatures more or less restless`() {
    val restlessness = driveFor(BestiaDomain.RESTLESSNESS)

    val atShippedSpeed = restlessness.amountOver(gameHours = 0.001f, realSeconds = 1f, asleep = false)
    val atEightTimesSpeed = restlessness.amountOver(gameHours = 0.008f, realSeconds = 1f, asleep = false)

    assertEquals(atShippedSpeed, atEightTimesSpeed, TOLERANCE, "restlessness is paced by the wall clock")
  }

  @Test
  fun `a drive brings its own carry slot rather than needing to be registered`() {
    val invented = Drive.perGameHour(BestiaDomain.HUNGER, 1f)

    assertEquals("hungerFraction", invented.fractionKey.name)
  }

  /** How many one-second sweeps a drive needs to climb from nothing to [target]. */
  private fun sweepsToReach(drive: Drive, target: Int): Int {
    var value = 0f
    var sweeps = 0

    while (value < target) {
      value += drive.amountOver(gameHoursPerRealSecond(), 1f, asleep = false)
      sweeps++

      if (sweeps > 600) return sweeps
    }

    return sweeps
  }

  private fun driveFor(key: StateKey<Int>): Drive {
    return BestiaDomain.DRIVES.first { it.key == key }
  }

  private fun perRealSecond(key: StateKey<Int>): Float {
    return driveFor(key).amountOver(gameHoursPerRealSecond(), realSeconds = 1f, asleep = false)
  }

  private fun asleepPerRealSecond(drive: Drive): Float {
    return drive.amountOver(gameHoursPerRealSecond(), realSeconds = 1f, asleep = true)
  }

  private fun gameHoursPerRealSecond(): Float {
    return BestiaDateTime.SPEED_FACTOR.toFloat() / AiDriveSystem.SECONDS_PER_GAME_HOUR
  }

  private companion object {
    const val TOLERANCE = 1e-4f
  }
}

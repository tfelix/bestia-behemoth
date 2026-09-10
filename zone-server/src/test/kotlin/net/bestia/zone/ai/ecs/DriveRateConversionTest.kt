package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.environment.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * That authoring a drive per in-game hour did not retune any creature.
 *
 * The rates used to be per real second, which reads as a mistake beside a townsperson who eats at noon: a
 * day here is eight real hours, so the two are two orders of magnitude apart in the same field. Restating
 * them in the unit a day is measured in is only safe if it is exactly a restatement, and these are the
 * numbers that were there before.
 */
class DriveRateConversionTest {

  @Test
  fun `the bestia drives still move at the rates they always did`() {
    assertEquals(0.55f, perRealSecond(BestiaDomain.HUNGER), TOLERANCE, "hunger")
    assertEquals(0.25f, perRealSecond(BestiaDomain.TIREDNESS), TOLERANCE, "tiredness")
    assertEquals(1.6f, perRealSecond(BestiaDomain.RESTLESSNESS), TOLERANCE, "restlessness")
  }

  @Test
  fun `tiredness still recovers twenty times faster than it accumulates`() {
    val tiredness = driveFor(BestiaDomain.TIREDNESS)

    assertEquals(-5.0f, asleepPerRealSecond(tiredness), TOLERANCE, "sleeping recovery")
    assertEquals(
      -20.0f,
      tiredness.whileSleepingPerGameHour / tiredness.perGameHour,
      TOLERANCE,
      "a full night has to be slept off well before dawn"
    )
  }

  @Test
  fun `nothing else changes while asleep`() {
    for (key in listOf(BestiaDomain.HUNGER, BestiaDomain.RESTLESSNESS)) {
      val drive = driveFor(key)
      assertEquals(
        drive.perGameHour,
        drive.whileSleepingPerGameHour,
        "lying down does not feed you and it certainly does not make you less bored"
      )
    }
  }

  @Test
  fun `a drive brings its own carry slot rather than needing to be registered`() {
    val invented = Drive(BestiaDomain.HUNGER, perGameHour = 1f)

    assertEquals("hungerFraction", invented.fractionKey.name)
  }

  private fun driveFor(key: net.bestia.zone.ai.core.state.StateKey<Int>): Drive {
    return BestiaDomain.DRIVES.first { it.key == key }
  }

  private fun perRealSecond(key: net.bestia.zone.ai.core.state.StateKey<Int>): Float {
    return driveFor(key).perGameHour * gameHoursPerRealSecond()
  }

  private fun asleepPerRealSecond(drive: Drive): Float {
    return drive.whileSleepingPerGameHour * gameHoursPerRealSecond()
  }

  private fun gameHoursPerRealSecond(): Float {
    return BestiaDateTime.SPEED_FACTOR.toFloat() / AiDriveSystem.SECONDS_PER_GAME_HOUR
  }

  private companion object {
    const val TOLERANCE = 1e-4f
  }
}

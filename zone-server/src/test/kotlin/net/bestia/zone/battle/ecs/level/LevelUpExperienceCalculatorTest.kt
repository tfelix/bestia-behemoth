package net.bestia.zone.battle.ecs.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelUpExperienceCalculatorTest {

  private val calculator = LevelUpExperienceCalculator()

  /** The table in the game docs, so the two cannot drift apart unnoticed. */
  @Test
  fun `matches the docs table`() {
    val docs = mapOf(1 to 22, 10 to 531, 25 to 12237, 50 to 135152, 75 to 554112, 100 to 1510426)

    docs.forEach { (level, exp) -> assertEquals(exp, calculator.getRequiredExperience(level), "level $level") }
  }

  @Test
  fun `a level at the anchor takes the anchor hours`() {
    assertEquals(24 * 3600.0, calculator.levelSeconds(97), 0.001)
  }

  @Test
  fun `a level takes longer the higher it is, and never less than the floor`() {
    assertEquals(60.0, calculator.levelSeconds(1), 0.001)
    assertEquals(295.0, calculator.levelSeconds(10), 1.0)
    assertTrue((2..150).all { calculator.levelSeconds(it) >= calculator.levelSeconds(it - 1) })
  }

  /** Fighting is on the curve: the kills a level needs, at the seconds a kill takes, are the level's time. */
  @Test
  fun `the kills of a level at the anchor fill the anchor hours`() {
    val killExp = 4 * 97 + 5
    val kills = calculator.getRequiredExperience(97).toDouble() / killExp

    assertEquals(24 * 3600.0, kills * 25, 25.0)
  }

  /** The curve this replaced dropped at every tenth level: level 20 needed less than level 19. */
  @Test
  fun `every level needs more than the one before`() {
    (2..150).forEach { level ->
      assertTrue(
        calculator.getRequiredExperience(level) > calculator.getRequiredExperience(level - 1),
        "level $level"
      )
    }
  }
}

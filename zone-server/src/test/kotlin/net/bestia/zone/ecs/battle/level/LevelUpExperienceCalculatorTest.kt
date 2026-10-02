package net.bestia.zone.ecs.battle.level

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelUpExperienceCalculatorTest {

  private val calculator = LevelUpExperienceCalculator()

  /** The table in the game docs, so the two cannot drift apart unnoticed. */
  @Test
  fun `matches the docs table`() {
    val docs = mapOf(10 to 363, 25 to 5261, 50 to 41757, 75 to 140753, 100 to 333498)

    docs.forEach { (level, exp) -> assertEquals(exp, calculator.getRequiredExperience(level), "level $level") }
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

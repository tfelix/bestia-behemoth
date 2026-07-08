package net.bestia.zone.ecs.battle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExperienceCalculatorTest {

  private val calculator = ExperienceCalculator()

  @Test
  fun `solo kill grants exactly the given exp`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 1,
    )

    assertEquals(100, result[1L])
  }

  @Test
  fun `solo kill splits proportionally to damage share`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 0.75f, 2L to 0.25f),
      attackingPlayerCount = 1,
    )

    assertEquals(75, result[1L])
    assertEquals(25, result[2L])
  }

  @Test
  fun `two players grant a 15 percent bonus on top of the base exp`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 0.5f, 2L to 0.5f),
      attackingPlayerCount = 2,
    )

    // total exp = floor(100 * 1.15) = 115, split evenly
    assertEquals(57, result[1L])
    assertEquals(57, result[2L])
  }

  @Test
  fun `bonus caps at 150 percent starting at 11 attacking players`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 11,
    )

    // total exp = floor(100 * 2.5) = 250
    assertEquals(250, result[1L])
  }

  @Test
  fun `bonus does not exceed the cap beyond 11 attacking players`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 50,
    )

    assertEquals(250, result[1L])
  }

  @Test
  fun `zero attacking players is treated like a solo kill`() {
    val result = calculator.calculate(
      givenExp = 100,
      damagePercentages = mapOf(1L to 1f),
      attackingPlayerCount = 0,
    )

    assertEquals(100, result[1L])
  }
}

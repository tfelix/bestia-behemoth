package net.bestia.zone.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

/**
 * The worked examples of `bestia-docs/content/docs/mechanics/bestia.md`. Their target is "at 1% HP" and they
 * count that as the full +30, so the target here is one hit point in ten thousand.
 */
class CaptureChanceCalculatorTest {

  private val sut = CaptureChanceCalculator()

  @ParameterizedTest(name = "Mega Trap, no Forester skills, WIL 100 vs Lv. {0}: {1}%")
  @CsvSource("100, 52", "110, 22", "120, 0", "140, 0", "150, 0")
  fun `a mega trap with no forester skills`(level: Int, expectedPercent: Int) {
    val chance = sut.chance(nearlyDead(TrapTier.MEGA_TRAP, level, willpower = 100))

    assertEquals(expectedPercent / 100.0, chance, TOLERANCE)
  }

  @ParameterizedTest(name = "Master Trap, maxed Forester, WIL 150 vs Lv. {0}: {1}%")
  @CsvSource("100, 95", "110, 95", "120, 88", "140, 28", "150, 0")
  fun `a master trap with maxed forester skills`(level: Int, expectedPercent: Int) {
    val input = nearlyDead(TrapTier.MASTER_TRAP, level, willpower = 150, bestiaTrapping = 5, beastfriend = 5)

    assertEquals(expectedPercent / 100.0, sut.chance(input), TOLERANCE)
  }

  @ParameterizedTest(name = "Bestia Trap vs a healthy Lv. {0}: {1}%")
  @CsvSource("1, 60", "20, 60", "21, 20", "41, 0", "100, 0")
  fun `a bestia trap against an unhurt target`(level: Int, expectedPercent: Int) {
    val input = CaptureChanceCalculator.Input(
      tier = TrapTier.BESTIA_TRAP,
      targetLevel = level,
      targetHp = 100,
      targetMaxHp = 100,
      trapperWillpower = 0,
      bestiaTrappingLevel = 0,
      beastfriendLevel = 0,
    )

    assertEquals(expectedPercent / 100.0, sut.chance(input), TOLERANCE)
  }

  @Test
  fun `a halved target adds fifteen points`() {
    val input = CaptureChanceCalculator.Input(
      tier = TrapTier.BESTIA_TRAP,
      targetLevel = 21,
      targetHp = 50,
      targetMaxHp = 100,
      trapperWillpower = 0,
      bestiaTrappingLevel = 0,
      beastfriendLevel = 0,
    )

    assertEquals(0.35, sut.chance(input), TOLERANCE)
  }

  private fun nearlyDead(
    tier: TrapTier,
    level: Int,
    willpower: Int,
    bestiaTrapping: Int = 0,
    beastfriend: Int = 0,
  ): CaptureChanceCalculator.Input {
    return CaptureChanceCalculator.Input(
      tier = tier,
      targetLevel = level,
      targetHp = 1,
      targetMaxHp = 10_000,
      trapperWillpower = willpower,
      bestiaTrappingLevel = bestiaTrapping,
      beastfriendLevel = beastfriend,
    )
  }

  private companion object {
    const val TOLERANCE = 0.001
  }
}

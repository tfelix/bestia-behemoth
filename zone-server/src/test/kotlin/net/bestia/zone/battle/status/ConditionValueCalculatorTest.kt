package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConditionValueCalculatorTest {

  private val calculator = ConditionValueCalculator()

  @Test
  fun `max HP grows with vitality and level`() {
    val base = calculator.computeMaxHp(level = 10, vitality = 10)

    assertTrue(calculator.computeMaxHp(level = 10, vitality = 20) > base, "higher VIT must raise max HP")
    assertTrue(calculator.computeMaxHp(level = 20, vitality = 10) > base, "higher level must raise max HP")
  }

  @Test
  fun `max mana grows with intelligence and level`() {
    val base = calculator.computeMaxMana(level = 10, intelligence = 10)

    assertTrue(calculator.computeMaxMana(level = 10, intelligence = 20) > base, "higher INT must raise max mana")
    assertTrue(calculator.computeMaxMana(level = 20, intelligence = 10) > base, "higher level must raise max mana")
  }

  @Test
  fun `max stamina grows with vitality, strength and willpower`() {
    val base = calculator.computeMaxStamina(level = 10, vitality = 10, strength = 10, willpower = 10)

    assertTrue(calculator.computeMaxStamina(level = 10, vitality = 20, strength = 10, willpower = 10) > base)
    assertTrue(calculator.computeMaxStamina(level = 10, vitality = 10, strength = 30, willpower = 10) > base)
    assertTrue(calculator.computeMaxStamina(level = 10, vitality = 10, strength = 10, willpower = 30) > base)
  }

  @Test
  fun `formulas match the documented values at level 1`() {
    // Locks the floor()/integer-division behavior so a formula tweak is a conscious change.
    assertEquals(18, calculator.computeMaxHp(level = 1, vitality = 10))
    assertEquals(29, calculator.computeMaxMana(level = 1, intelligence = 10))
    assertEquals(29, calculator.computeMaxStamina(level = 1, vitality = 10, strength = 10, willpower = 10))
  }
}

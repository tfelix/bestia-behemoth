package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DerivedStatusValuesTest {

  @Test
  fun `strength adds its tenth squared on top of itself`() {
    val derived = DerivedStatusValues.fromStatusValues(lv = 40, sv = statusValues(strength = 55))

    // 40/4 + 55 + 5² + 10/5 + 10/3
    assertEquals(10 + 55 + 25 + 2 + 3, derived.atk)
  }

  @Test
  fun `a ranged attack squares dexterity instead`() {
    val derived = DerivedStatusValues.fromStatusValues(lv = 40, sv = statusValues(dexterity = 55))

    // 40/4 + 55 + 5² + 10/5 + 10/3
    assertEquals(10 + 55 + 25 + 2 + 3, derived.rangedAtk)
  }

  @Test
  fun `intelligence adds its seventh squared to magic attack`() {
    val derived = DerivedStatusValues.fromStatusValues(lv = 40, sv = statusValues(intelligence = 70))

    // 40/4 + 70 + 10² + 10/5
    assertEquals(10 + 70 + 100 + 2, derived.matk)
  }

  @Test
  fun `bonuses add to hit, flee and crit after the attributes`() {
    val plain = DerivedStatusValues.fromStatusValues(lv = 40, sv = statusValues())
    val geared = DerivedStatusValues.fromStatusValues(
      lv = 40,
      sv = statusValues(),
      hitBonus = 15,
      fleeBonus = 12,
      critBonus = 3
    )

    assertEquals(plain.hitrate + 15, geared.hitrate)
    assertEquals(plain.flee + 12, geared.flee)
    assertEquals(plain.crit + 3, geared.crit)
  }

  private fun statusValues(strength: Int = 10, dexterity: Int = 10, intelligence: Int = 10) = StatusValues(
    agility = 10,
    strength = strength,
    dexterity = dexterity,
    intelligence = intelligence,
    vitality = 10,
    willpower = 10
  )
}

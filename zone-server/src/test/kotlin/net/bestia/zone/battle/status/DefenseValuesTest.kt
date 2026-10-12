package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefenseValuesTest {

  @Test
  fun `soft defence is half the vitality, and low vitality rolls nothing on top`() {
    val defense = DefenseValues.fromStatusValues(lv = 50, sv = statusValues(vitality = 10))

    assertEquals(5, defense.defense)
    assertEquals(0, defense.defenseSpread)
  }

  @Test
  fun `high vitality adds a roll that grows with its square`() {
    val defense = DefenseValues.fromStatusValues(lv = 50, sv = statusValues(vitality = 60))

    assertEquals(30, defense.defense)
    // 60 * (60 - 15) / 150
    assertEquals(18, defense.defenseSpread)
  }

  @Test
  fun `level, strength and agility no longer add soft defence`() {
    val low = DefenseValues.fromStatusValues(lv = 1, sv = statusValues(vitality = 40))
    val high = DefenseValues.fromStatusValues(lv = 99, sv = statusValues(vitality = 40, strength = 99, agility = 99))

    assertEquals(low.defense, high.defense)
    assertEquals(low.defenseSpread, high.defenseSpread)
  }

  private fun statusValues(vitality: Int, strength: Int = 10, agility: Int = 10) = StatusValues(
    agility = agility,
    strength = strength,
    dexterity = 10,
    intelligence = 10,
    vitality = vitality,
    willpower = 10
  )
}

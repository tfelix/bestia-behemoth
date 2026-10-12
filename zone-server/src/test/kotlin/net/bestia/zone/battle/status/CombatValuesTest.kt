package net.bestia.zone.battle.status

import net.bestia.zone.battle.Weapon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CombatValuesTest {

  // STR 6, AGI 6, VIT 9, INT 8, DEX 10, WIL 13.
  private val base = StatusValues(
    strength = 6, vitality = 9, intelligence = 8, agility = 6, willpower = 13, dexterity = 10
  )

  private fun contextOf(attributes: StatusValues = base) = StatusValueRecalcContext(attributes, baseSpeed = 1f)

  @Test
  fun `an entity wearing nothing resolves to the documented formulas`() {
    val values = CombatValues.ofBase(level = 20, base = base)

    // Level 20 is 5 of every `BaseLv/4`.
    // The squared terms: (STR / 10)² is still 0 at 6 strength, while (INT / 7)² is already 1 at 8 intelligence.
    assertEquals(5 + 6 + 0 + 10 / 5 + 13 / 3, values.derived.atk)
    assertEquals(5 + 8 + 1 + 13 / 5, values.derived.matk)
    assertEquals(175 + 20 + 10 + 13 / 3, values.derived.hitrate)
    assertEquals(100 + 20 + 6 + 13 / 5, values.derived.flee)
    assertEquals(13 / 3, values.derived.crit)
    // Soft defence is half the vitality now, and nine vitality rolls nothing on top.
    assertEquals(9 / 2, values.defense.defense)
    assertEquals(0, values.defense.defenseSpread)
    assertEquals(Weapon.NONE, values.weapon)
    assertEquals(0, values.defense.hardDefense)
  }

  @Test
  fun `the seed is what an empty recalc resolves to, so the two cannot drift`() {
    assertEquals(
      CombatValues.ofBase(level = 20, base = base),
      CombatValues.fromContext(level = 20, base = base, context = contextOf())
    )
  }

  @Test
  fun `the derived numbers are the ones the damage path derives`() {
    val values = CombatValues.ofBase(level = 37, base = base)

    assertEquals(DerivedStatusValues.fromStatusValues(37, base), values.derived)
    assertEquals(DefenseValues.fromStatusValues(37, base), values.defense)
  }

  @Test
  fun `an unbuffed block reuses its base attributes`() {
    val values = CombatValues.fromContext(level = 20, base = base, context = contextOf())

    assertSame(values.base, values.attributes)
  }

  @Test
  fun `a buff changes the effective attributes and what derives from them, not the base`() {
    val context = contextOf()
    context.strength += 5

    val values = CombatValues.fromContext(level = 20, base = base, context = context)

    assertEquals(6, values.base.strength)
    assertEquals(11, values.attributes.strength)
    // Strength 11 is five more points, and ten of them start the squared term.
    assertEquals(CombatValues.ofBase(20, base).derived.atk + 5 + 1, values.derived.atk)
    assertNotEquals(CombatValues.ofBase(20, base), values)
  }

  @Test
  fun `a worn weapon and armour land in the block`() {
    val context = contextOf()
    context.addAttack(atk = 10, matk = 7)
    context.addDefense(hardDefense = 4, hardMagicDefense = 2)
    context.weaponUpgradeLevel = 3

    val values = CombatValues.fromContext(level = 20, base = base, context = context)

    assertEquals(Weapon(atk = 10, matk = 7, upgradeLevel = 3), values.weapon)
    // What a swing spends: the un-refined attack plus the refinement on top.
    assertEquals(16, values.weapon.physicalAtk)
    assertEquals(4, values.defense.hardDefense)
    assertEquals(2, values.defense.hardMagicDefense)
  }

  @Test
  fun `the object baseline is the all-ones level-one block props and sites have always had`() {
    assertEquals(
      CombatValues.ofBase(
        level = 1,
        base = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
      ),
      CombatValues.OBJECT_BASELINE
    )
  }
}

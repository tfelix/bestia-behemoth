package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.item.profession.Profession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KnightPassivesTest {

  private val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

  @Test
  fun `Heavy Weapon Mastery adds three ATK per level with a Knight's weapon`() {
    context.heldWeaponType = WeaponType.POLEARM

    HeavyWeaponMastery().apply(context, 10)

    assertEquals(30, context.attack)
  }

  @Test
  fun `Heavy Weapon Mastery adds nothing with a dagger or bare hands`() {
    HeavyWeaponMastery().apply(context, 10)
    context.heldWeaponType = WeaponType.DAGGER
    HeavyWeaponMastery().apply(context, 10)

    assertEquals(0, context.attack)
  }

  @Test
  fun `Heavy Weapon Mastery counts exactly the Knight's weapons`() {
    assertEquals(Profession.KNIGHT.weapons, HeavyWeaponMastery.KNIGHT_WEAPONS)
  }

  @Test
  fun `Heavy Armor Mastery adds two hard DEF per level for each heavy piece`() {
    context.wornArmorTypes = listOf(ArmorType.HEAVY, ArmorType.MEDIUM, ArmorType.HEAVY)

    HeavyArmorMastery().apply(context, 3)

    assertEquals(2 * 3 * 2, context.hardDefense)
  }

  @Test
  fun `Heavy Armor Mastery adds nothing without heavy armor`() {
    context.wornArmorTypes = listOf(ArmorType.LIGHT)

    HeavyArmorMastery().apply(context, 10)

    assertEquals(0, context.hardDefense)
  }
}

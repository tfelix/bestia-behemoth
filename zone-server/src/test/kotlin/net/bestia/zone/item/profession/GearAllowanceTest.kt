package net.bestia.zone.item.profession

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GearAllowanceTest {

  private val sageKnight = GearAllowance.of(listOf(Profession.SAGE, Profession.KNIGHT))

  @Test
  fun `a weapon either profession allows may be held`() {
    assertTrue(sageKnight.allows(weapon(WeaponType.TOME, EquipmentSlot.LEFT_HAND)))
    assertTrue(sageKnight.allows(weapon(WeaponType.POLEARM, twoHanded = true)))
    assertFalse(sageKnight.allows(weapon(WeaponType.BOW, twoHanded = true)))
  }

  @Test
  fun `the heavier armor of the two counts, and lighter weights with it`() {
    assertTrue(sageKnight.allows(armor(ArmorType.HEAVY)))
    assertTrue(GearAllowance.of(listOf(Profession.HUNTER)).allows(armor(ArmorType.CLOTH)))
    assertFalse(GearAllowance.of(listOf(Profession.HUNTER)).allows(armor(ArmorType.MEDIUM)))
  }

  @Test
  fun `cloth, slings and tools need no profession, and novice gear ignores them`() {
    assertTrue(GearAllowance.BASE.allows(weapon(WeaponType.SLING)))
    assertTrue(GearAllowance.BASE.allows(weapon(WeaponType.TOOL)))
    assertTrue(GearAllowance.BASE.allows(armor(ArmorType.CLOTH)))
    assertTrue(GearAllowance.BASE.allows(weapon(WeaponType.SWORD, noviceOnly = true)))
    assertFalse(GearAllowance.BASE.allows(weapon(WeaponType.SWORD)))
  }

  private fun weapon(
    type: WeaponType,
    slot: EquipmentSlot = EquipmentSlot.RIGHT_HAND,
    twoHanded: Boolean = false,
    noviceOnly: Boolean = false,
  ) = Item(
    id = 1, identifier = "test_${type.name.lowercase()}", weight = 1, type = Item.ItemType.EQUIP,
    equipSlot = slot, weaponType = type, twoHanded = twoHanded, noviceOnly = noviceOnly,
    material = ItemMaterial.METAL,
  )

  private fun armor(type: ArmorType) = Item(
    id = 2, identifier = "test_armor", weight = 1, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.ARMOR, armorType = type, material = ItemMaterial.CLOTH,
  )
}

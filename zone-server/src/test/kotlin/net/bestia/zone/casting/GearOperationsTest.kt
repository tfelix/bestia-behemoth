package net.bestia.zone.casting

import io.mockk.mockk
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GearOperationsTest {

  private val templates = itemTemplatesOf(DAGGER, SHIELD)
  private val gear = GearOperations(EquipmentChanges(templates), mockk(), mockk(), templates)
  private val world = testWorld()

  @Test
  fun `a dagger in the left hand is dual wielding, a shield is not`() {
    val duelist = wearerWith(DAGGER)
    val knight = wearerWith(SHIELD)
    val bare = world.createEntity { id -> add(id, Equipment(EquipmentSlots.ALL)) }

    assertTrue(gear.isDualWielding(world, duelist))
    assertFalse(gear.isDualWielding(world, knight))
    assertFalse(gear.isDualWielding(world, bare))
  }

  private fun wearerWith(leftHand: Item): Long {
    return world.createEntity { id ->
      val equipment = Equipment(EquipmentSlots.ALL)
      equipment.equip(EquipmentSlot.LEFT_HAND, Equipment.EquippedItem(itemId = leftHand.id, uniqueId = 1L))
      add(id, equipment)
    }
  }

  private companion object {
    val DAGGER = Item(
      id = 1L, identifier = "dagger", weight = 10, type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.RIGHT_HAND,
      weaponType = WeaponType.DAGGER, material = ItemMaterial.METAL
    )
    val SHIELD = Item(
      id = 2L, identifier = "shield", weight = 100, type = Item.ItemType.EQUIP, equipSlot = EquipmentSlot.LEFT_HAND,
      material = ItemMaterial.WOOD
    )
  }
}

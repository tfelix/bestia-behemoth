package net.bestia.zone.item.ecs

import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquipmentChangesTest {

  private val changes = EquipmentChanges()
  private val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
  private val sword = Inventory.Item(itemId = SWORD_ID, amount = 1, uniqueId = 77L, stackable = false, durability = 30, maxDurability = 50)
  private val inventory = Inventory(mutableListOf(sword))

  @Test
  fun `putting on marks the bag copy as worn and carries its wear into the slot`() {
    val worn = changes.putOn(equipment, inventory, EquipmentSlot.RIGHT_HAND, sword, itemLevel = 3)

    assertEquals(77L, worn?.uniqueId)
    assertEquals(30, equipment.get(EquipmentSlot.RIGHT_HAND)?.durability)
    assertTrue(sword.equipped)
  }

  @Test
  fun `a taken slot refuses and leaves the bag alone`() {
    equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD_ID, uniqueId = 99L))

    assertNull(changes.putOn(equipment, inventory, EquipmentSlot.RIGHT_HAND, sword, itemLevel = 3))
    assertFalse(sword.equipped)
  }

  @Test
  fun `taking off clears the bag marker`() {
    changes.putOn(equipment, inventory, EquipmentSlot.RIGHT_HAND, sword, itemLevel = 3)

    val removed = changes.takeOff(equipment, inventory, EquipmentSlot.RIGHT_HAND)

    assertEquals(77L, removed?.uniqueId)
    assertFalse(sword.equipped)
    assertNull(equipment.get(EquipmentSlot.RIGHT_HAND))
  }

  private companion object {
    const val SWORD_ID = 11L
  }
}

package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EquipmentChangesTest {

  private val changes = EquipmentChanges(
    ItemTemplateRegistry(mockk<ItemRepository> { every { findAll() } returns listOf(SWORD, TORCH) })
  )
  private val equipment = Equipment(availableSlotMask = EquipmentSlots.ALL)
  private val sword = Inventory.Item(itemId = SWORD.id, amount = 1, uniqueId = 77L, stackable = false, durability = 30, maxDurability = 50)
  private val torches = Inventory.Item(itemId = TORCH.id, amount = 5, weight = 30)
  private val inventory = Inventory(mutableListOf(sword, torches))

  @Test
  fun `putting on marks the bag copy as worn and carries its wear into the slot`() {
    val worn = changes.putOn(equipment, inventory, EquipmentSlot.RIGHT_HAND, sword, itemLevel = 3)

    assertEquals(77L, worn?.uniqueId)
    assertEquals(30, equipment.get(EquipmentSlot.RIGHT_HAND)?.durability)
    assertTrue(sword.equipped)
  }

  @Test
  fun `a taken slot refuses and leaves the bag alone`() {
    equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD.id, uniqueId = 99L))

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

  @Test
  fun `a pile is worn as one fresh copy split off it`() {
    val worn = changes.putOn(equipment, inventory, EquipmentSlot.LIGHT_SOURCE, torches, itemLevel = 1) { 500L }

    assertEquals(500L, worn?.uniqueId)
    assertEquals(TORCH.maxDurability, worn?.durability)
    assertEquals(4, torches.amount)
    val copy = inventory.byPlayerItemId(500L)!!
    assertTrue(copy.equipped)
    assertEquals(TORCH.maxDurability, copy.maxDurability)
  }

  @Test
  fun `an unused copy goes back into its pile when taken off`() {
    changes.putOn(equipment, inventory, EquipmentSlot.LIGHT_SOURCE, torches, itemLevel = 1) { 500L }

    changes.takeOff(equipment, inventory, EquipmentSlot.LIGHT_SOURCE)

    assertEquals(5, torches.amount)
    assertNull(inventory.byPlayerItemId(500L))
  }

  @Test
  fun `a used copy stays a copy of its own when taken off`() {
    equipment.equip(
      EquipmentSlot.LIGHT_SOURCE,
      Equipment.EquippedItem(itemId = TORCH.id, uniqueId = 600L, durability = 10, maxDurability = TORCH.maxDurability)
    )
    inventory.addItem(Inventory.Item(itemId = TORCH.id, amount = 1, uniqueId = 600L, equipped = true, durability = 10))

    changes.takeOff(equipment, inventory, EquipmentSlot.LIGHT_SOURCE)

    assertEquals(5, torches.amount)
    assertFalse(inventory.byPlayerItemId(600L)!!.equipped)
  }

  private companion object {
    val SWORD = Item(
      id = 11L, identifier = "sword", weight = 10, type = Item.ItemType.EQUIP,
      equipSlot = EquipmentSlot.RIGHT_HAND, material = ItemMaterial.METAL
    )
    val TORCH = Item(
      id = 35L, identifier = "torch", weight = 30, type = Item.ItemType.EQUIP,
      equipSlot = EquipmentSlot.LIGHT_SOURCE, stackable = true, maxDurability = 360, material = ItemMaterial.WOOD
    )
  }
}

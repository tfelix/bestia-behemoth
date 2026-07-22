package net.bestia.zone.item.equip

import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.Item
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EquipmentServiceTest {

  private val service = EquipmentService()

  private val boots = Item(
    id = 4L, identifier = "boots", weight = 8, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.FOOTGEAR
  )
  private val apple = Item(id = 1L, identifier = "apple", weight = 1, type = Item.ItemType.ETC)

  private fun inventoryOf(vararg items: Inventory.Item) = Inventory(items.toMutableList())

  private fun held(item: Item, uniqueId: Long = 77L) =
    Inventory.Item(itemId = item.id, amount = 1, weight = item.weight, uniqueId = uniqueId, stackable = false)

  @Test
  fun `a held item going into an available slot is accepted`() {
    val equipment = Equipment(EquipmentSlots.ALL)

    assertNull(service.checkEquip(equipment, inventoryOf(held(boots)), boots, EquipmentSlot.FOOTGEAR, 77L))
  }

  @Test
  fun `a slot outside the wearer's mask is refused`() {
    // A blob-like species: armor and garment only, no footgear.
    val equipment = Equipment(EquipmentSlots.maskOf(EquipmentSlot.ARMOR, EquipmentSlot.GARMENT))

    assertEquals(
      EquipmentService.Denial.SLOT_NOT_AVAILABLE,
      service.checkEquip(equipment, inventoryOf(held(boots)), boots, EquipmentSlot.FOOTGEAR, 77L)
    )
  }

  @Test
  fun `an item put into a slot it does not belong in is refused`() {
    val equipment = Equipment(EquipmentSlots.ALL)

    assertEquals(
      EquipmentService.Denial.NOT_ALLOWED,
      service.checkEquip(equipment, inventoryOf(held(boots)), boots, EquipmentSlot.ARMOR, 77L)
    )
  }

  @Test
  fun `a non-equipment item is refused`() {
    val equipment = Equipment(EquipmentSlots.ALL)

    assertEquals(
      EquipmentService.Denial.NOT_ALLOWED,
      service.checkEquip(equipment, inventoryOf(held(apple, uniqueId = 0L)), apple, EquipmentSlot.ARMOR, 0L)
    )
  }

  @Test
  fun `an item the wearer does not hold is refused`() {
    val equipment = Equipment(EquipmentSlots.ALL)

    assertEquals(
      EquipmentService.Denial.ITEM_NOT_FOUND,
      service.checkEquip(equipment, inventoryOf(), boots, EquipmentSlot.FOOTGEAR, 77L)
    )
  }

  @Test
  fun `an item that is already worn can not be equipped a second time`() {
    val equipment = Equipment(EquipmentSlots.ALL)
    equipment.equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = boots.id, uniqueId = 77L))

    assertEquals(
      EquipmentService.Denial.ITEM_NOT_FOUND,
      service.checkEquip(equipment, inventoryOf(held(boots)), boots, EquipmentSlot.FOOTGEAR, 77L)
    )
  }
}

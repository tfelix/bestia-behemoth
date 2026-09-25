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
    equipSlot = EquipmentSlot.FOOTGEAR, level = BOOTS_LEVEL
  )
  private val noviceBoots = Item(
    id = 33L, identifier = "novice_boots", weight = 8, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.FOOTGEAR, level = 1, noviceOnly = true
  )
  private val apple = Item(id = 1L, identifier = "apple", weight = 1, type = Item.ItemType.ETC)

  private fun inventoryOf(vararg items: Inventory.Item) = Inventory(items.toMutableList())

  private fun held(item: Item, uniqueId: Long = 77L) =
    Inventory.Item(itemId = item.id, amount = 1, weight = item.weight, uniqueId = uniqueId, stackable = false)

  /**
   * Defaults for the two wearer arguments, so a test that is about a structural rule does not have to say
   * anything about the wearer - and so that adding a third wearer rule touches one line rather than ten.
   */
  private fun check(
    item: Item,
    slot: EquipmentSlot,
    equipment: Equipment = Equipment(EquipmentSlots.ALL),
    inventory: Inventory = inventoryOf(held(item)),
    heldUniqueId: Long = 77L,
    wearerLevel: Int = WELL_ABOVE_ITEM_LEVEL,
    wearerIsNovice: Boolean = true
  ): EquipmentService.Denial? {
    return service.checkEquip(equipment, inventory, item, slot, heldUniqueId, wearerLevel, wearerIsNovice)
  }

  @Test
  fun `a held item going into an available slot is accepted`() {
    assertNull(check(boots, EquipmentSlot.FOOTGEAR))
  }

  @Test
  fun `a slot outside the wearer's mask is refused`() {
    // A blob-like species: armor and garment only, no footgear.
    val equipment = Equipment(EquipmentSlots.maskOf(EquipmentSlot.ARMOR, EquipmentSlot.GARMENT))

    assertEquals(
      EquipmentService.Denial.SLOT_NOT_AVAILABLE,
      check(boots, EquipmentSlot.FOOTGEAR, equipment = equipment)
    )
  }

  @Test
  fun `an item put into a slot it does not belong in is refused`() {
    assertEquals(
      EquipmentService.Denial.NOT_ALLOWED,
      check(boots, EquipmentSlot.ARMOR)
    )
  }

  @Test
  fun `a non-equipment item is refused`() {
    assertEquals(
      EquipmentService.Denial.NOT_ALLOWED,
      check(apple, EquipmentSlot.ARMOR, inventory = inventoryOf(held(apple, uniqueId = 0L)), heldUniqueId = 0L)
    )
  }

  @Test
  fun `an item the wearer does not hold is refused`() {
    assertEquals(
      EquipmentService.Denial.ITEM_NOT_FOUND,
      check(boots, EquipmentSlot.FOOTGEAR, inventory = inventoryOf())
    )
  }

  @Test
  fun `a wearer below the item's level is refused`() {
    assertEquals(
      EquipmentService.Denial.LEVEL_TOO_LOW,
      check(boots, EquipmentSlot.FOOTGEAR, wearerLevel = BOOTS_LEVEL - 1)
    )
  }

  /** Exactly the item's level is enough - the table reads as a minimum, not as something to exceed. */
  @Test
  fun `a wearer exactly at the item's level is accepted`() {
    assertNull(check(boots, EquipmentSlot.FOOTGEAR, wearerLevel = BOOTS_LEVEL))
  }

  /**
   * An entity with no `Level` component at all reaches the service as level 0, and being unqualified is the
   * safe reading of not knowing.
   */
  @Test
  fun `a wearer of no known level is refused anything above tier one`() {
    assertEquals(
      EquipmentService.Denial.LEVEL_TOO_LOW,
      check(boots, EquipmentSlot.FOOTGEAR, wearerLevel = 0)
    )
  }

  @Test
  fun `an item that is already worn can not be equipped a second time`() {
    val equipment = Equipment(EquipmentSlots.ALL)
    equipment.equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = boots.id, uniqueId = 77L))

    assertEquals(
      EquipmentService.Denial.ITEM_NOT_FOUND,
      check(boots, EquipmentSlot.FOOTGEAR, equipment = equipment)
    )
  }

  @Test
  fun `novice-only gear on a novice is accepted`() {
    assertNull(check(noviceBoots, EquipmentSlot.FOOTGEAR))
  }

  @Test
  fun `novice-only gear on a specialised wearer is refused`() {
    assertEquals(
      EquipmentService.Denial.NOVICE_ONLY,
      check(noviceBoots, EquipmentSlot.FOOTGEAR, wearerIsNovice = false)
    )
  }

  /** The flag gates only the items that carry it; ordinary gear does not care what its wearer has learned. */
  @Test
  fun `ordinary gear is unaffected by the wearer no longer being a novice`() {
    assertNull(check(boots, EquipmentSlot.FOOTGEAR, wearerIsNovice = false))
  }

  /**
   * What the revalidation sweep asks, and the reason it cannot simply call `checkEquip`: that one refuses an
   * item it finds already worn, which is every item the sweep is there to judge.
   */
  @Test
  fun `checkStillWearable judges a worn item without minding that it is worn`() {
    assertEquals(
      EquipmentService.Denial.NOVICE_ONLY,
      service.checkStillWearable(noviceBoots, WELL_ABOVE_ITEM_LEVEL, wearerIsNovice = false)
    )
    assertNull(service.checkStillWearable(noviceBoots, WELL_ABOVE_ITEM_LEVEL, wearerIsNovice = true))
  }

  private companion object {
    const val BOOTS_LEVEL = 10

    /** High enough that the structural cases never trip the level rule as well. */
    const val WELL_ABOVE_ITEM_LEVEL = 99
  }
}

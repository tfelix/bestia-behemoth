package net.bestia.zone.auction

import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.material.ItemMaterial
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuctionCategoryTest {

  @Test
  fun `equipment is grouped by where it is worn`() {
    assertEquals(AuctionCategory.WEAPON, AuctionCategory.of(equip(EquipmentSlot.RIGHT_HAND)))
    assertEquals(AuctionCategory.ARMOR, AuctionCategory.of(equip(EquipmentSlot.LEFT_HAND)))
    assertEquals(AuctionCategory.ACCESSORY, AuctionCategory.of(equip(EquipmentSlot.ACCESSORY_2)))
  }

  @Test
  fun `a light source is grouped with the consumables`() {
    assertEquals(AuctionCategory.CONSUMABLE, AuctionCategory.of(equip(EquipmentSlot.LIGHT_SOURCE)))
  }

  @Test
  fun `everything else is grouped by what it is for`() {
    assertEquals(AuctionCategory.CONSUMABLE, AuctionCategory.of(item(Item.ItemType.USABLE)))
    assertEquals(AuctionCategory.MATERIAL, AuctionCategory.of(item(Item.ItemType.ETC)))
  }

  private fun equip(slot: EquipmentSlot): Item {
    return item(Item.ItemType.EQUIP, slot)
  }

  private fun item(type: Item.ItemType, slot: EquipmentSlot? = null): Item {
    return Item(
      identifier = "test_${type.name.lowercase()}",
      weight = 1,
      type = type,
      equipSlot = slot,
      script = if (type == Item.ItemType.USABLE) "heal" else null,
      material = ItemMaterial.entries.first(),
    )
  }
}

package net.bestia.zone.auction

import net.bestia.bnet.proto.AuctionCategoryProto
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.Item

/** How the browse filter groups goods. Stored by name on each listing, so a search needs no join. */
enum class AuctionCategory {
  WEAPON,
  ARMOR,
  ACCESSORY,
  CONSUMABLE,
  MATERIAL;

  companion object {

    fun of(item: Item): AuctionCategory {
      return when (item.type) {
        Item.ItemType.USABLE -> CONSUMABLE
        Item.ItemType.ETC -> MATERIAL
        Item.ItemType.EQUIP -> when (item.equipSlot) {
          EquipmentSlot.RIGHT_HAND -> WEAPON
          EquipmentSlot.ACCESSORY_1, EquipmentSlot.ACCESSORY_2 -> ACCESSORY
          // A torch burns down like a potion is drunk, so buyers look for it next to them.
          EquipmentSlot.LIGHT_SOURCE -> CONSUMABLE
          else -> ARMOR
        }
      }
    }

    /** Null for "any". */
    fun fromProto(category: AuctionCategoryProto.AuctionCategory): AuctionCategory? {
      return when (category) {
        AuctionCategoryProto.AuctionCategory.AUCTION_CATEGORY_WEAPON -> WEAPON
        AuctionCategoryProto.AuctionCategory.AUCTION_CATEGORY_ARMOR -> ARMOR
        AuctionCategoryProto.AuctionCategory.AUCTION_CATEGORY_ACCESSORY -> ACCESSORY
        AuctionCategoryProto.AuctionCategory.AUCTION_CATEGORY_CONSUMABLE -> CONSUMABLE
        AuctionCategoryProto.AuctionCategory.AUCTION_CATEGORY_MATERIAL -> MATERIAL
        else -> null
      }
    }
  }
}

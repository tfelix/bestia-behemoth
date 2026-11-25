package net.bestia.zone.item.keepsake

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemInstance
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** The bag of a bestia that fell under a Keepsake Ward: one warded bundle on the ground, see [KeepsakeWardEnchantment]. */
@Service
class KeepsakeBags(
  private val inventoryService: InventoryService,
  private val bundles: ItemBundles,
  private val lootItems: LootItemEntitySpawner,
  private val transactions: TransactionTemplate,
) {

  class Packed(val removed: List<ItemContainer.DetachedItem>, val bundle: ItemInstance?)

  /** Off the tick, in the master's job. The bag leaves and the bundle is made in one transaction. */
  fun pack(owner: OwnedBestia): Packed {
    return transactions.execute {
      val removed = inventoryService.removeAllFreeFromOwner(owner.masterId, owner.playerBestiaId)
      Packed(removed, bundles.pack(removed))
    } ?: Packed(emptyList(), null)
  }

  fun drop(world: World, bundle: ItemInstance, masterId: Long, at: Vec3L) {
    lootItems.spawnLootItem(
      world,
      itemId = ItemBundles.ITEM_ID,
      amount = 1,
      pos = at,
      uniqueId = bundle.id,
      heldWeight = bundle.heldWeight,
      enchantments = listOf(KeepsakeWardEnchantment.forMaster(masterId)),
    )
  }
}

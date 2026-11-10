package net.bestia.zone.item.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.util.EntityId

// Goods that changed hands in the database are mirrored into the live inventory afterwards. The database
// moves first, so a crash between the two loses nothing: the next login reads the truth.

private val LOG = KotlinLogging.logger { }

/** The live bag that shows what this container holds. Reads its lazy slots, so call it outside the world scope. */
fun ItemContainer.toLiveInventory(itemStats: ItemStatCatalog): Inventory {
  return Inventory(
    items = slots.map { slot ->
      Inventory.Item(
        itemId = slot.template.id,
        amount = slot.amount,
        weight = slot.weight,
        uniqueId = slot.uniqueId,
        stackable = slot.isStackable,
        equipped = slot.isEquipped,
        durability = slot.durability,
        maxDurability = slot.maxDurability,
        slots = slot.slots,
        upgradeLevel = slot.itemInstance?.upgradeLevel ?: 0,
        itemLevel = itemStats.itemLevelOf(slot),
        rolledStats = slot.rolledStats,
        provenance = slot.heldProvenance
      )
    }.toMutableList()
  )
}

/** An entity without a live inventory is offline or despawning; it gets [items] with its next login. */
fun WorldView.grantToLive(entityId: EntityId, items: List<ReservedItem>) {
  modify(entityId) { id ->
    val inventory = get(id, Inventory::class)

    if (inventory == null) {
      LOG.debug { "Entity $entityId has no live inventory to receive ${items.size} item(s)" }
      return@modify
    }

    items.forEach { inventory.addItem(it.toLiveItem()) }
  }
}

/** @return false when the live inventory does not hold what the database just gave up */
fun WorldView.takeFromLive(entityId: EntityId, item: ReservedItem): Boolean {
  return modify(entityId) { id ->
    val inventory = get(id, Inventory::class) ?: return@modify false

    val held = when {
      item.uniqueId != 0L -> inventory.byPlayerItemId(item.uniqueId)
      else -> inventory.getItems().firstOrNull {
        it.itemId == item.itemId && it.isStackable && it.provenance == item.provenance
      }
    }

    held != null && inventory.take(held.playerItemId, item.amount)
  } ?: false
}

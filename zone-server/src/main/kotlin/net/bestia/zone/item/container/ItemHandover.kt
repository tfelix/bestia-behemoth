package net.bestia.zone.item.container

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Moves goods between two of a master's own holders that the caller already checked: ownership, reach, room.
 *
 * The goods leave the giver's live bag on the tick, so from then on nothing else - a second move, a drop, a craft -
 * can reach them. The durable move follows as one transaction on the master's job key, the key every other write
 * to these containers uses. Only that transaction failing gives the goods back: then the database never changed.
 * When it finds the giver no longer holds them, the live bag showed more than the database had, and they stay
 * gone. At no point do both bags hold the same goods.
 */
@Component
class ItemHandover(
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
) {

  /** @return false when nothing moved: the giver does not hold that much, or the move could not be queued */
  fun hand(world: World, masterId: Long, from: Party, to: Party, playerItemId: Long, amount: Int): Boolean {
    val inventory = world.get(from.entityId, Inventory::class) ?: return false
    val held = inventory.byPlayerItemId(playerItemId)?.takeIf { it.amount >= amount } ?: return false

    val taken = held.portion(amount)
    inventory.take(playerItemId, amount)

    val queued = asyncJobExecutor.trySubmit(masterId) { handDurably(masterId, from, to, taken) }
    if (!queued) {
      LOG.warn { "Handover of item ${taken.itemId} for master $masterId was not queued; the giver keeps it" }
      inventory.addItem(taken)
    }

    return queued
  }

  private fun handDurably(masterId: Long, from: Party, to: Party, taken: Inventory.Item) {
    val moved: ReservedItem? = try {
      inventoryService.transferBetweenOwners(
        masterId = masterId,
        from = from.holder,
        to = to.holder,
        itemId = taken.itemId,
        uniqueId = taken.uniqueId,
        amount = taken.amount,
        provenance = taken.provenance,
      )
    } catch (e: Exception) {
      LOG.error(e) { "Handover of item ${taken.itemId} for master $masterId rolled back; the giver gets it back" }
      worldView.modify(from.entityId) { id -> get(id, Inventory::class)?.addItem(taken) }
      return
    }

    if (moved == null) {
      LOG.warn {
        "Entity ${from.entityId} showed ${taken.amount}x item ${taken.itemId} its container does not freely hold; " +
          "nothing is handed over"
      }
      return
    }

    worldView.grantToLive(to.entityId, listOf(moved))
  }

  /** A detached copy of [amount] of this held item, to give back or to name to the database. */
  private fun Inventory.Item.portion(amount: Int): Inventory.Item {
    return Inventory.Item(
      itemId = itemId,
      amount = amount,
      weight = weight,
      uniqueId = uniqueId,
      stackable = stackable,
      durability = durability,
      maxDurability = maxDurability,
      slots = slots,
      upgradeLevel = upgradeLevel,
      itemLevel = itemLevel,
      rolledStats = rolledStats,
      provenance = provenance,
    )
  }

  /** One end of a handover: the live entity and the container it stands for. */
  data class Party(val entityId: EntityId, val holder: ItemHolder)

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}

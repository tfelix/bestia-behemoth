package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Drops an item to the ground. The durable removal comes first and gates everything else, so a ground
 * item always stands for an item the database no longer holds: a drop can lose an item, never copy one.
 */
@Component
class DropItemHandler(
  private val inventoryService: InventoryService,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val connectionInfoService: ConnectionInfoService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<DropItemCMSG> {
  override val handles = DropItemCMSG::class

  override fun handle(msg: DropItemCMSG): Boolean {
    if (msg.amount <= 0) {
      LOG.warn { "Invalid drop amount ${msg.amount} from player ${msg.playerId}" }
      return true
    }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    val holdsIt = world.read { get(activeEntityId, Inventory::class)?.holds(msg.itemId, msg.amount) } ?: false
    if (!holdsIt) {
      LOG.warn { "Entity $activeEntityId holds no ${msg.amount} of item ${msg.itemId} to drop" }
      return true
    }

    // Keyed by the master like every other write to its container, so a concurrent use or trade of the same
    // item queues behind this one and finds it gone.
    asyncJobExecutor.submit(masterId) { dropDurably(msg, masterId, activeEntityId) }

    return true
  }

  private fun dropDurably(msg: DropItemCMSG, masterId: Long, activeEntityId: EntityId) {
    val removed = inventoryService.removeOneFromMaster(masterId, msg.itemId, msg.amount, msg.uniqueId)
    if (removed == null) {
      LOG.warn { "Could not remove ${msg.amount} of item ${msg.itemId} (uniqueId ${msg.uniqueId}) from master $masterId in DB" }
      return
    }

    val dropped = world.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify null
      val amount = inventory.mirrorRemoval(msg, removed) ?: return@modify null
      val pos = get(id, Position::class)?.toVec3L() ?: return@modify null

      Dropped(amount, Vec3L(pos.x + Random.nextLong(-1, 2), pos.y + Random.nextLong(-1, 2), pos.z))
    }

    if (dropped == null) {
      LOG.warn { "Item ${msg.itemId} left master $masterId in DB but not its live inventory; nothing is dropped" }
      return
    }

    lootItemEntitySpawner.spawnLootItem(
      world,
      itemId = msg.itemId,
      amount = dropped.amount,
      pos = dropped.pos,
      uniqueId = removed.uniqueId
    )
  }

  private fun Inventory.holds(itemId: Long, amount: Int): Boolean {
    return getItems().filter { it.itemId == itemId && !it.equipped }.sumOf { it.amount } >= amount
  }

  /** Takes off the live inventory what the database just removed; the amount that left, or null if it was not there. */
  private fun Inventory.mirrorRemoval(msg: DropItemCMSG, removed: ItemContainer.RemovedItem): Int? {
    if (removed.uniqueId == 0L) {
      return if (removeFromStack(msg.itemId, msg.amount)) msg.amount else null
    }

    // An instance minted this session is still 0 in the live mirror, so it can only be found by template.
    val mirrored = removeByUniqueId(removed.uniqueId) || removeInstanceOf(msg.itemId)

    return if (mirrored) 1 else null
  }

  private data class Dropped(
    val amount: Int,
    val pos: Vec3L,
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

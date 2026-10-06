package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.random.Random
import net.bestia.zone.ecs.core.WorldView

/**
 * Drops an item to the ground. The durable removal comes first and gates everything else, so a ground
 * item always stands for an item the database no longer holds: a drop can lose an item, never copy one.
 */
@Component
class DropItemHandler(
  private val inventoryService: InventoryService,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldView: WorldView,
) : TickMessageHandler<DropItemCMSG> {
  override val wire = decoder(MessageCase.DROP_ITEM) { accountId, envelope ->
    DropItemCMSG.fromBnet(accountId, envelope.dropItem)
  }

  override fun handle(world: World, msg: DropItemCMSG): Boolean {
    if (msg.amount <= 0) {
      LOG.warn { "Invalid drop amount ${msg.amount} from player ${msg.playerId}" }
      return true
    }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, activeEntityId, "drop an item")) {
      return true
    }
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    val inventory = world.get(activeEntityId, Inventory::class)
    val holdsIt = inventory != null && holdsDroppable(inventory, world.get(activeEntityId, Equipment::class), msg)
    if (!holdsIt) {
      LOG.warn { "Entity $activeEntityId does not hold ${msg.amount}x item ${msg.itemId} (uniqueId ${msg.uniqueId})" }
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

    val dropped = worldView.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify null
      val amount = inventory.mirrorRemoval(msg, removed) ?: return@modify null
      val pos = get(id, Position::class)?.toVec3L() ?: return@modify null

      Dropped(amount, Vec3L(pos.x + Random.nextLong(-1, 2), pos.y + Random.nextLong(-1, 2), pos.z))
    }

    if (dropped == null) {
      LOG.warn { "Item ${msg.itemId} left master $masterId in DB but not its live inventory; nothing is dropped" }
      return
    }

    worldView.read {
      lootItemEntitySpawner.spawnLootItem(
        this,
        itemId = msg.itemId,
        amount = dropped.amount,
        pos = dropped.pos,
        uniqueId = removed.uniqueId
      )
    }
  }

  /**
   * Checked before the durable removal, which cannot be taken back: the database prefers an instance over a pile,
   * so any instance of the template can be dropped, and a pile only for the full amount.
   */
  private fun holdsDroppable(inventory: Inventory, equipment: Equipment?, msg: DropItemCMSG): Boolean {
    if (equipment != null && !equipment.leavesUnwornCopy(inventory, msg.itemId, msg.uniqueId)) {
      return false
    }

    val copies = inventory.getItems().filter { it.itemId == msg.itemId }

    if (msg.uniqueId != 0L) {
      return copies.any { it.uniqueId == msg.uniqueId }
    }

    return copies.any { !it.isStackable } || copies.filter { it.isStackable }.sumOf { it.amount } >= msg.amount
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

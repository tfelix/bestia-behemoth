package net.bestia.zone.item.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
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
    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)

    val held = world.get(activeEntityId, Inventory::class)?.byPlayerItemId(msg.playerItemId)
    if (held == null || !mayDrop(held, world.get(activeEntityId, Equipment::class), msg.amount)) {
      LOG.warn { "Entity $activeEntityId does not hold ${msg.amount}x of item ${msg.playerItemId} to drop" }
      return true
    }

    // Keyed by the master like every other write to its container, so a concurrent use or trade of the same
    // item queues behind this one and finds it gone.
    asyncJobExecutor.submit(masterId) { dropDurably(msg, held, masterId, playerBestiaId, activeEntityId) }

    return true
  }

  private fun dropDurably(
    msg: DropItemCMSG,
    held: Inventory.Item,
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    activeEntityId: EntityId
  ) {
    val itemId = held.itemId
    val removed = inventoryService.removeNamedFromOwner(
      masterId, playerBestiaId, itemId, msg.amount, held.uniqueId, held.provenance
    )
    if (removed == null) {
      LOG.warn { "Could not remove ${msg.amount} of item $itemId (uniqueId ${held.uniqueId}) from entity $activeEntityId in DB" }
      return
    }

    val dropped = worldView.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify null
      if (!inventory.take(msg.playerItemId, msg.amount)) return@modify null
      val pos = get(id, Position::class)?.toVec3L() ?: return@modify null

      Vec3L(pos.x + Random.nextLong(-1, 2), pos.y + Random.nextLong(-1, 2), pos.z)
    }

    if (dropped == null) {
      LOG.warn { "Item $itemId left master $masterId in DB but not its live inventory; nothing is dropped" }
      return
    }

    worldView.read {
      lootItemEntitySpawner.spawnLootItem(
        this,
        itemId = itemId,
        amount = msg.amount,
        pos = dropped,
        uniqueId = removed.uniqueId
      )
    }
  }

  /** Checked before the durable removal, which cannot be taken back. Worn gear stays on until it is taken off. */
  private fun mayDrop(held: Inventory.Item, equipment: Equipment?, amount: Int): Boolean {
    val worn = held.equipped || equipment?.isWorn(held.uniqueId) == true

    return !worn && held.amount >= amount
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

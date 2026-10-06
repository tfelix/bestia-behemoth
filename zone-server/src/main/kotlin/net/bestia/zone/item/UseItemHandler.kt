package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UseItemHandler(
  private val itemScriptExecutionService: ItemScriptExecutionService,
  private val itemRepository: ItemRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val deadActionGuard: DeadActionGuard,
  private val world: WorldView
) : IoMessageHandler<UseItemCMSG> {
  override val wire = decoder(MessageCase.USE_ITEM) { accountId, envelope ->
    UseItemCMSG.fromBnet(accountId, envelope.useItem)
  }

  override fun handle(msg: UseItemCMSG): Boolean {
    val item = itemRepository.findByIdOrNull(msg.itemId)

    if (item == null) {
      LOG.warn { "Item ${msg.itemId} was not found in the database" }
      return true
    }

    if (item.type != Item.ItemType.USABLE) {
      LOG.warn { "Item ${item.identifier} was not usable but account ${msg.playerId} tried to use it" }
      return true
    }

    // Get the currently selected entity for this player
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (world.read { deadActionGuard.refuses(this, activeEntityId, "use an item") }) {
      return true
    }

    val holdsItem = world.read { get(activeEntityId, Inventory::class)?.hasItem(msg.itemId.toInt()) == true }
    if (!holdsItem) {
      LOG.warn { "Entity $activeEntityId owned no item ${msg.itemId}" }
      return true
    }

    // Durable first and outside the world lock: an effect cannot be taken back, so it only happens once the
    // database has given the item up. Taking it from the live inventory alone let a copy the database no longer
    // held be used again and again.
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val removed = inventoryService.removeOneFromMaster(masterId, item.id, 1)
    if (removed == null) {
      LOG.warn { "Master $masterId holds no ${item.identifier} in the database, refusing to use it" }
      return true
    }

    // `this` is the full World, valid only within this lock-held scope.
    val consumed = runCatching {
      world.modify(activeEntityId) { id -> itemScriptExecutionService.useItem(this, id, item, msg.args) }
    }

    if (consumed.getOrNull() != true) {
      asyncJobExecutor.submit(key = masterId) {
        inventoryService.grantToMaster(masterId, item.id, 1, removed.uniqueId)
      }
    }
    consumed.getOrThrow()

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

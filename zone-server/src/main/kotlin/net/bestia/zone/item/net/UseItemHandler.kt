package net.bestia.zone.item.net

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
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.skill.NoviceGate
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import net.bestia.bnet.proto.OperationErrorProto
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository

@Component
class UseItemHandler(
  private val itemScriptExecutionService: ItemScriptExecutionService,
  private val itemRepository: ItemRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val deadActionGuard: DeadActionGuard,
  private val noviceGate: NoviceGate,
  private val outMessageProcessor: OutMessageProcessor,
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

    // Asked before the database gives the item up, so a refusal costs nothing and needs nothing given back.
    if (item.noviceOnly && !isNovice(activeEntityId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OperationErrorProto.OpError.ITEM_NOVICE_ONLY))
      return true
    }

    // Durable first and outside the world lock: an effect cannot be taken back, so it only happens once the
    // database has given the item up. Taking it from the live inventory alone let a copy the database no longer
    // held be used again and again.
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)
    val removed = inventoryService.removeOneFromOwner(masterId, playerBestiaId, item.id, 1)
    if (removed == null) {
      LOG.warn { "Entity $activeEntityId holds no ${item.identifier} in the database, refusing to use it" }
      return true
    }

    // `this` is the full World, valid only within this lock-held scope.
    val consumed = runCatching {
      world.modify(activeEntityId) { id -> itemScriptExecutionService.useItem(this, id, item, msg.args) }
    }

    if (consumed.getOrNull() != true) {
      asyncJobExecutor.submit(key = masterId) {
        inventoryService.grantToOwner(masterId, playerBestiaId, item.id, 1, removed.uniqueId)
      }
    }
    consumed.getOrThrow()

    return true
  }

  private fun isNovice(entityId: EntityId): Boolean {
    return world.read { noviceGate.isNovice(get(entityId, KnownSkills::class)) }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

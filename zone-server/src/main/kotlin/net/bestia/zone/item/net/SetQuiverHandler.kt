package net.bestia.zone.item.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/**
 * Points the active entity's quiver at a list of ammo items. Nothing moves: the ammo stays in the bag and is
 * only looked up when a shot is fired, so an item need not be held to be pointed at.
 */
@Component
class SetQuiverHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val templates: ItemTemplateRegistry,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val deadActionGuard: DeadActionGuard,
) : TickMessageHandler<SetQuiverCMSG> {
  override val wire = decoder(MessageCase.SET_QUIVER) { accountId, envelope ->
    SetQuiverCMSG.fromBnet(accountId, envelope.setQuiver)
  }

  override fun handle(world: World, msg: SetQuiverCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, activeEntityId, "fill the quiver")) {
      return true
    }

    val accepted = world.modify(activeEntityId) { id ->
      val equipment = get(id, Equipment::class) ?: return@modify false

      // The client only lets ammo into the quiver, so anything else is a client bug or a crafted packet.
      val accepted = msg.itemIds.all { templates.templateOf(it)?.isAmmo == true } && equipment.setQuiver(msg.itemIds)
      if (!accepted) {
        equipment.markDirty()
      }

      accepted
    }

    if (accepted != true) {
      LOG.warn { "Account ${msg.playerId} sent an invalid quiver ${msg.itemIds}" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.EQUIP_NOT_ALLOWED))
      return true
    }

    val masterId = connectionInfoService.getMasterId(msg.playerId)
    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)
    asyncJobExecutor.submit(key = masterId) {
      inventoryService.setQuiver(masterId, playerBestiaId, msg.itemIds)
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

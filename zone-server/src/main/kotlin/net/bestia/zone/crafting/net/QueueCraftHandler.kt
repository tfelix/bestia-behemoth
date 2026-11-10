package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.crafting.station.StationQueueService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class QueueCraftHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val stationQueues: StationQueueService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<QueueCraftCMSG> {
  override val wire = decoder(MessageCase.QUEUE_CRAFT) { accountId, envelope ->
    QueueCraftCMSG.fromBnet(accountId, envelope.queueCraft)
  }

  override fun handle(world: World, msg: QueueCraftCMSG): Boolean {
    val actorId = connectionInfoService.getActiveEntityId(msg.playerId)

    stationQueues.enqueue(world, actorId, msg.structureId, msg.recipeId, msg.amount)
      ?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }
}

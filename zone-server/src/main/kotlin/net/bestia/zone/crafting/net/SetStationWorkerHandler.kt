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
class SetStationWorkerHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val stationQueues: StationQueueService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<SetStationWorkerCMSG> {
  override val wire = decoder(MessageCase.SET_STATION_WORKER) { accountId, envelope ->
    SetStationWorkerCMSG.fromBnet(accountId, envelope.setStationWorker)
  }

  override fun handle(world: World, msg: SetStationWorkerCMSG): Boolean {
    val actorId = connectionInfoService.getActiveEntityId(msg.playerId)

    val refusal = if (msg.anyFreeCrafter) {
      stationQueues.takeAnyFreeCrafter(world, actorId, msg.structureId)
    } else {
      stationQueues.setWorker(world, actorId, msg.structureId, msg.workerEntityId)
    }
    refusal?.let { outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }

    return true
  }
}

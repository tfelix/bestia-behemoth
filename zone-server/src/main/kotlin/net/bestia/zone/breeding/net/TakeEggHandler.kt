package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.breeding.BreedingFarmService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class TakeEggHandler(
  private val connections: ConnectionInfoService,
  private val farms: BreedingFarmService,
  private val out: OutMessageProcessor,
) : TickMessageHandler<TakeEggCMSG> {
  override val wire = decoder(MessageCase.TAKE_EGG) { accountId, envelope ->
    TakeEggCMSG.fromBnet(accountId, envelope.takeEgg)
  }

  override fun handle(world: World, msg: TakeEggCMSG): Boolean {
    val masterEntityId = connections.getSelectedMasterEntityId(msg.playerId)
    farms.takeEgg(world, masterEntityId, msg.structureId)
      ?.let { out.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }
    return true
  }
}

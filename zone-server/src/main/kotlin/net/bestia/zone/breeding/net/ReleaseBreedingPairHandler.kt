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
class ReleaseBreedingPairHandler(
  private val connections: ConnectionInfoService,
  private val farms: BreedingFarmService,
  private val out: OutMessageProcessor,
) : TickMessageHandler<ReleaseBreedingPairCMSG> {
  override val wire = decoder(MessageCase.RELEASE_BREEDING_PAIR) { accountId, envelope ->
    ReleaseBreedingPairCMSG.fromBnet(accountId, envelope.releaseBreedingPair)
  }

  override fun handle(world: World, msg: ReleaseBreedingPairCMSG): Boolean {
    val masterEntityId = connections.getSelectedMasterEntityId(msg.playerId)
    farms.release(world, masterEntityId, msg.structureId)
      ?.let { out.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }
    return true
  }
}

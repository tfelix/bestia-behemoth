package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.breeding.BreedingFarmService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** On the IO lane, because a bestia's sex is read from its row; everything else is checked in the world scope. */
@Component
class StartBreedingHandler(
  private val connections: ConnectionInfoService,
  private val playerBestias: PlayerBestiaRepository,
  private val farms: BreedingFarmService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : IoMessageHandler<StartBreedingCMSG> {
  override val wire = decoder(MessageCase.START_BREEDING) { accountId, envelope ->
    StartBreedingCMSG.fromBnet(accountId, envelope.startBreeding)
  }

  override fun handle(msg: StartBreedingCMSG): Boolean {
    val masterEntityId = connections.getSelectedMasterEntityId(msg.playerId)
    val sexes = listOf(msg.motherEntityId, msg.fatherEntityId)
      .mapNotNull { entityId -> playerBestias.findByEntityId(entityId)?.sex?.let { entityId to it } }
      .toMap()

    val error = world.modify(masterEntityId) { id ->
      farms.start(this, id, msg.structureId, msg.motherEntityId, msg.fatherEntityId, sexes)
    }
    error?.let { out.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }
    return true
  }
}

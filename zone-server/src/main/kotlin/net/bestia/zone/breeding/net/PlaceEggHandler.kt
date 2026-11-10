package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.breeding.EggSummary
import net.bestia.zone.breeding.IncubatorService
import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** On the IO lane, because what the egg holds is read from its row; the rest is checked in the world scope. */
@Component
class PlaceEggHandler(
  private val connections: ConnectionInfoService,
  private val eggs: BestiaEggRepository,
  private val incubators: IncubatorService,
  private val world: WorldView,
  private val out: OutMessageProcessor,
) : IoMessageHandler<PlaceEggCMSG> {
  override val wire = decoder(MessageCase.PLACE_EGG) { accountId, envelope ->
    PlaceEggCMSG.fromBnet(accountId, envelope.placeEgg)
  }

  override fun handle(msg: PlaceEggCMSG): Boolean {
    val masterEntityId = connections.getSelectedMasterEntityId(msg.playerId)
    val egg = eggs.findByIdOrNull(msg.uniqueId)?.let(EggSummary::of)

    val error = if (egg == null) {
      OpError.BREEDING_NOT_POSSIBLE
    } else {
      world.modify(masterEntityId) { id -> incubators.place(this, id, msg.structureId, egg) }
    }
    error?.let { out.sendToPlayer(msg.playerId, OperationErrorSMSG(it)) }
    return true
  }
}

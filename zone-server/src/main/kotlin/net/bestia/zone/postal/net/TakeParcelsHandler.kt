package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.office.PostOfficeDesk
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.townsfolk.conversation.TalkService
import org.springframework.stereotype.Component

@Component
class TakeParcelsHandler(
  private val connections: ConnectionInfoService,
  private val talk: TalkService,
  private val desk: PostOfficeDesk,
) : TickMessageHandler<TakeParcelsCMSG> {

  override val wire = decoder(MessageCase.TAKE_PARCELS) { accountId, envelope ->
    TakeParcelsCMSG(accountId, envelope.takeParcels.postmasterEntityId, envelope.takeParcels.parcelIdsList)
  }

  override fun handle(world: World, msg: TakeParcelsCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val postmaster = talk.speakerInReach(msg.playerId, actor, msg.postmasterEntityId) ?: return true
    desk.takeParcels(world, msg.playerId, postmaster, msg.parcelIds)

    return true
  }
}

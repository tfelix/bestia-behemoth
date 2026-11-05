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
class SendParcelHandler(
  private val connections: ConnectionInfoService,
  private val talk: TalkService,
  private val desk: PostOfficeDesk,
) : TickMessageHandler<SendParcelCMSG> {

  override val wire = decoder(MessageCase.SEND_PARCEL) { accountId, envelope ->
    val send = envelope.sendParcel
    val lines = PostOfficeDesk.Line.allOf(send.itemsList)
    SendParcelCMSG(accountId, send.postmasterEntityId, send.recipientName, lines, send.coachOnly)
  }

  override fun handle(world: World, msg: SendParcelCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val postmaster = talk.speakerInReach(msg.playerId, actor, msg.postmasterEntityId) ?: return true
    desk.send(msg.playerId, postmaster, msg.recipientName, msg.lines, msg.coachOnly)

    return true
  }
}

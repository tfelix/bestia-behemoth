package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.office.PostOfficeDesk
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.postal.office.PostCounters
import org.springframework.stereotype.Component

@Component
class QuoteParcelHandler(
  private val connections: ConnectionInfoService,
  private val counters: PostCounters,
  private val desk: PostOfficeDesk,
) : TickMessageHandler<QuoteParcelCMSG> {

  override val wire = decoder(MessageCase.QUOTE_PARCEL) { accountId, envelope ->
    val quote = envelope.quoteParcel
    val lines = PostOfficeDesk.Line.allOf(quote.itemsList)
    QuoteParcelCMSG(accountId, quote.postmasterEntityId, quote.recipientName, lines)
  }

  override fun handle(world: World, msg: QuoteParcelCMSG): Boolean {
    val actor = connections.getActiveEntityId(msg.playerId)
    val counter = counters.inReach(world, msg.playerId, actor, msg.postmasterEntityId) ?: return true
    desk.quote(msg.playerId, counter, msg.recipientName, msg.lines)

    return true
  }
}

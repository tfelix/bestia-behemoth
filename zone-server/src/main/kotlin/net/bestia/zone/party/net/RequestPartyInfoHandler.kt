package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyRosterPublisher
import org.springframework.stereotype.Component

/**
 * The client asks for its roster, which it does whenever its party window loads. Answered with the roster or, in
 * no party, an empty one - not an error, since being in no party is ordinary.
 */
@Component
class RequestPartyInfoHandler(
  private val rosterPublisher: PartyRosterPublisher,
) : IoMessageHandler<RequestPartyInfoCMSG> {

  override val wire = decoder(MessageCase.REQUEST_PARTY_INFO) { accountId, envelope ->
    RequestPartyInfoCMSG.fromBnet(accountId, envelope.requestPartyInfo)
  }

  override fun handle(msg: RequestPartyInfoCMSG): Boolean {
    rosterPublisher.sendTo(msg.playerId)

    return true
  }
}

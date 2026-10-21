package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/** The invited player joins. Everyone in the party, the newcomer included, gets the new roster. */
@Component
class AcceptPartyInviteHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<AcceptPartyInviteCMSG> {

  override val wire = decoder(MessageCase.ACCEPT_PARTY_INVITE) { accountId, envelope ->
    AcceptPartyInviteCMSG.fromBnet(accountId, envelope.acceptPartyInvite)
  }

  override fun handle(msg: AcceptPartyInviteCMSG): Boolean {
    try {
      val partyId = partyService.acceptInvitation(msg.playerId, msg.invitationId)

      rosterPublisher.request(partyId)
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}

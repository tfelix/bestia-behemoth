package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/** A member leaves; the owner leaving disbands the party, since there is no ownership transfer. */
@Component
class LeavePartyHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<LeavePartyCMSG> {

  override val wire = decoder(MessageCase.LEAVE_PARTY) { accountId, envelope ->
    LeavePartyCMSG.fromBnet(accountId, envelope.leaveParty)
  }

  override fun handle(msg: LeavePartyCMSG): Boolean {
    try {
      when (val result = partyService.leaveParty(msg.playerId)) {
        is PartyService.LeavePartyResult.Disbanded -> rosterPublisher.disbanded(result.partyId, result.members)
        is PartyService.LeavePartyResult.Left -> rosterPublisher.removed(result.partyId, result.member)
      }
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}

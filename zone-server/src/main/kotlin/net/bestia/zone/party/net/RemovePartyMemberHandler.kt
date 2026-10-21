package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/** The owner kicks a member. The kicked member is told the party is over for them, the rest get the new roster. */
@Component
class RemovePartyMemberHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<RemovePartyMemberCMSG> {

  override val wire = decoder(MessageCase.REMOVE_PARTY_MEMBER) { accountId, envelope ->
    RemovePartyMemberCMSG.fromBnet(accountId, envelope.removePartyMember)
  }

  override fun handle(msg: RemovePartyMemberCMSG): Boolean {
    try {
      val removed = partyService.removeMember(msg.playerId, msg.memberMasterId)

      rosterPublisher.removed(removed.partyId, removed.member)
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}

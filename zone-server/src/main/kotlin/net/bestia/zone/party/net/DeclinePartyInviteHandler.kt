package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/** The invited player says no. The inviter is told who, the way a declined trade is. */
@Component
class DeclinePartyInviteHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<DeclinePartyInviteCMSG> {

  override val wire = decoder(MessageCase.DECLINE_PARTY_INVITE) { accountId, envelope ->
    DeclinePartyInviteCMSG.fromBnet(accountId, envelope.declinePartyInvite)
  }

  override fun handle(msg: DeclinePartyInviteCMSG): Boolean {
    try {
      val declined = partyService.declineInvitation(msg.playerId, msg.invitationId)

      outMessageProcessor.sendToPlayer(
        declined.inviterAccountId,
        OperationErrorSMSG(OpError.PARTY_INVITE_DECLINED, listOf(declined.invitedMasterName))
      )
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}

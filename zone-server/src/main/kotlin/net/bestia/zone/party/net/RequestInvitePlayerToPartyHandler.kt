package net.bestia.zone.party.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class RequestInvitePlayerToPartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor,
  private val basicSkillGate: BasicSkillGate
) : IoMessageHandler<RequestPartyInvitationCMSG> {

  override val wire = decoder(MessageCase.REQUEST_PARTY_INVITATION) { accountId, envelope ->
    RequestPartyInvitationCMSG.fromBnet(accountId, envelope.requestPartyInvitation)
  }

  override fun handle(msg: RequestPartyInvitationCMSG): Boolean {
    // The inviter's rank, not the invitee's: joining a party is not what Basic Skill rank 5 unlocks, growing
    // one is - and refusing an invitation because the person invited has not bought a novice skill would make
    // the gate somebody else's problem.
    if (!basicSkillGate.mayParty(msg.playerId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_PARTY_LOCKED))
      return true
    }

    try {
      val sent = partyService.invitePlayerToParty(msg.playerId, msg.invitedMasterName)

      outMessageProcessor.sendToPlayer(sent.invitedAccountId, sent.invitation)
      outMessageProcessor.sendToPlayer(msg.playerId, OperationSuccessSMSG(OpSuccess.PARTY_INVITATION_SENT))
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }
}

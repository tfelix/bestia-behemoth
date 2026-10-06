package net.bestia.zone.party.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyInvitationExpired
import net.bestia.zone.party.PartyInviteDeclinedSMSG
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

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
      val inviterAccountId = partyService.declineInvitation(msg.playerId, msg.invitationId)

      outMessageProcessor.sendToPlayer(inviterAccountId, PartyInviteDeclinedSMSG(msg.invitationId))
    } catch (_: PartyInvitationExpired) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.INVITE_EXPIRED))
    } catch (e: PartyException) {
      LOG.error(e) { "Failed to process party invitation decline from player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

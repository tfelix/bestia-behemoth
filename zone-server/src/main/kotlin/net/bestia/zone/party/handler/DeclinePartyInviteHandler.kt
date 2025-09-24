package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.party.DeclinePartyInviteCMSG
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyInviteDeclinedSMSG
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class DeclinePartyInviteHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<DeclinePartyInviteCMSG> {

  override val handles = DeclinePartyInviteCMSG::class

  override fun handle(msg: DeclinePartyInviteCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      partyService.declineInvitation(msg.playerId, msg.invitationId)

      outMessageProcessor.sendToPlayer(0, PartyInviteDeclinedSMSG(msg.invitationId))
    } catch (e: PartyException) {
      LOG.error(e) { "Failed to process party invitation decline from player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

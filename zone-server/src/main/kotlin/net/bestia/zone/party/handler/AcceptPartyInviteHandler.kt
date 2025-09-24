package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.party.AcceptPartyInviteCMSG
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class AcceptPartyInviteHandler(
  private val partyService: PartyService,
) : InMessageProcessor.IncomingMessageHandler<AcceptPartyInviteCMSG> {

  override val handles = AcceptPartyInviteCMSG::class

  override fun handle(msg: AcceptPartyInviteCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      partyService.acceptInvitation(msg.playerId, msg.invitationId)
    } catch (e: Exception) {
      LOG.error(e) { "Failed to process party invitation acceptance from player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

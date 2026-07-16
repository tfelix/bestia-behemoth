package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.AcceptPartyInviteCMSG
import net.bestia.zone.party.AlreadyInPartyException
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyFullException
import net.bestia.zone.party.PartyInvitationExpired
import net.bestia.zone.party.PartyInviteForbiddenException
import net.bestia.zone.party.PartyNotFoundException
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class AcceptPartyInviteHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<AcceptPartyInviteCMSG> {

  override val handles = AcceptPartyInviteCMSG::class

  override fun handle(msg: AcceptPartyInviteCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      partyService.acceptInvitation(msg.playerId, msg.invitationId)

      val partyInfo = partyService.getPartyInfoForAccount(msg.playerId)
      if (partyInfo != null) {
        outMessageProcessor.sendToPlayer(msg.playerId, partyInfo)
      }
    } catch (_: PartyInvitationExpired) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.INVITE_EXPIRED))
    } catch (_: PartyInviteForbiddenException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PERMISSION))
    } catch (_: PartyNotFoundException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
    } catch (_: PartyFullException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PARTY_FULL))
    } catch (_: AlreadyInPartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.ALREADY_IN_PARTY))
    } catch (e: Exception) {
      LOG.error(e) { "Failed to process party invitation acceptance from player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

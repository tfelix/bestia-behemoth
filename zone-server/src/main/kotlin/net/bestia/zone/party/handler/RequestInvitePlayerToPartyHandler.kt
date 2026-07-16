package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.AlreadyInPartyException
import net.bestia.zone.party.NotPartyException
import net.bestia.zone.party.NotPartyOwnerException
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyFullException
import net.bestia.zone.party.PartyInvitationCreatedSMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.RequestPartyInvitationCMSG
import org.springframework.stereotype.Component

@Component
class RequestInvitePlayerToPartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<RequestPartyInvitationCMSG> {

  override val handles = RequestPartyInvitationCMSG::class

  override fun handle(msg: RequestPartyInvitationCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      val invitation = partyService.invitePlayerToParty(
        msg.playerId,
        msg.invitedAccountId
      )

      outMessageProcessor.sendToPlayer(msg.invitedAccountId, invitation)
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        PartyInvitationCreatedSMSG(
          invitationId = invitation.invitationId,
          invitedAccountId = msg.invitedAccountId,
          status = PartyInvitationCreatedSMSG.InvitationStatus.CREATED
        )
      )
    } catch (_: MasterNotFoundException) {
      LOG.error { "Failed to process party invitation: master for account ${msg.invitedAccountId} not found" }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PLAYER_NOT_FOUND))
    } catch (_: NotPartyException) {
      LOG.error { "Failed to process party invitation: account ${msg.playerId} not member of a party" }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
    } catch (_: NotPartyOwnerException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PERMISSION))
    } catch (_: PartyFullException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PARTY_FULL))
    } catch (_: AlreadyInPartyException) {
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        PartyInvitationCreatedSMSG(
          invitationId = 0,
          invitedAccountId = msg.invitedAccountId,
          status = PartyInvitationCreatedSMSG.InvitationStatus.ALREADY_IN_PARTY
        )
      )
    } catch (e: Exception) {
      LOG.error(e) { "Failed to process party invitation from player ${msg.playerId} to account ${msg.invitedAccountId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

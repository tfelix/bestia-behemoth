package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.NotPartyMemberException
import net.bestia.zone.party.NotPartyOwnerException
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyNotFoundException
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.RemovePartyMemberCMSG
import org.springframework.stereotype.Component

/**
 * Owner-initiated removal of a party member ("kick"). Notifies the removed member their
 * membership ended, then pushes the updated roster back to the (still in-party) requester.
 */
@Component
class RemovePartyMemberHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<RemovePartyMemberCMSG> {

  override val handles = RemovePartyMemberCMSG::class

  override fun handle(msg: RemovePartyMemberCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      val removedAccountId = partyService.removeMember(msg.playerId, msg.partyId, msg.memberAccountId)

      outMessageProcessor.sendToPlayer(removedAccountId, DisbandPartySMSG(msg.partyId))

      val partyInfo = partyService.getPartyInfoForAccount(msg.playerId)
      if (partyInfo != null) {
        outMessageProcessor.sendToPlayer(msg.playerId, partyInfo)
      }
    } catch (_: NotPartyOwnerException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PERMISSION))
    } catch (_: NotPartyMemberException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NOT_PARTY_MEMBER))
    } catch (_: PartyNotFoundException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
    } catch (e: Exception) {
      LOG.error(e) { "Failed to remove party member ${msg.memberAccountId} from party ${msg.partyId} by ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.LeavePartyCMSG
import net.bestia.zone.party.NotPartyException
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class LeavePartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<LeavePartyCMSG> {

  override val handles = LeavePartyCMSG::class

  override fun handle(msg: LeavePartyCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      when (val result = partyService.leaveParty(msg.playerId)) {
        is PartyService.LeavePartyResult.Disbanded -> {
          val disbandMsg = DisbandPartySMSG(result.partyId)
          outMessageProcessor.sendToPlayer(msg.playerId, disbandMsg)
          result.notifiedAccountIds.forEach { outMessageProcessor.sendToPlayer(it, disbandMsg) }
        }

        is PartyService.LeavePartyResult.Left -> {
          outMessageProcessor.sendToPlayer(msg.playerId, DisbandPartySMSG(result.partyId))

          result.remainingMemberAccountIds.forEach { accountId ->
            val partyInfo = partyService.getPartyInfoForAccount(accountId) ?: return@forEach
            outMessageProcessor.sendToPlayer(accountId, partyInfo)
          }
        }
      }
    } catch (_: NotPartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
    } catch (e: Exception) {
      LOG.error(e) { "Failed to process party leave for player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

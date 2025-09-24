package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyNotFoundException
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.RequestDisbandPartyCMSG
import org.springframework.stereotype.Component

@Component
class RequestDisbandPartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<RequestDisbandPartyCMSG> {

  override val handles = RequestDisbandPartyCMSG::class

  override fun handle(msg: RequestDisbandPartyCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      val oldPartyMemberAccountIds = partyService.disbandParty(msg.playerId, msg.partyId)
      val disbandMsg = DisbandPartySMSG(msg.partyId)

      oldPartyMemberAccountIds.forEach { oldPartyMemberAccountId ->
        outMessageProcessor.sendToPlayer(oldPartyMemberAccountId, disbandMsg)
      }

      LOG.debug { "Player ${msg.playerId} disbanded party ${msg.partyId}" }
    } catch (_: PartyNotFoundException) {
      LOG.error { "Failed to disband party ${msg.partyId} for player ${msg.playerId}: party not found" }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
    } catch (e: Exception) {
      LOG.error(e) { "Failed to disband party ${msg.partyId} for player ${msg.playerId}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

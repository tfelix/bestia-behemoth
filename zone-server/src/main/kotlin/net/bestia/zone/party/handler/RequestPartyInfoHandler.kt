package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.RequestPartyInfoCMSG
import org.springframework.stereotype.Component

@Component
class RequestPartyInfoHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<RequestPartyInfoCMSG> {

  override val handles = RequestPartyInfoCMSG::class

  override fun handle(msg: RequestPartyInfoCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      val partyInfo = partyService.getPartyInfoForAccount(msg.playerId)

      if (partyInfo != null) {
        LOG.debug { "Sent party info to player ${msg.playerId} for party ${partyInfo.partyId}" }
        outMessageProcessor.sendToPlayer(msg.playerId, partyInfo)
      } else {
        LOG.debug { "Player ${msg.playerId} requested party info but is not in a party" }
        outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
      }

      return true
    } catch (e: Exception) {
      LOG.error(e) { "Failed to get party info for player ${msg.playerId}" }
      return false
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

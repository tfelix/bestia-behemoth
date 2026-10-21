package net.bestia.zone.party.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class RequestDisbandPartyHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<RequestDisbandPartyCMSG> {

  override val wire = decoder(MessageCase.REQUEST_DISBAND_PARTY) { accountId, envelope ->
    RequestDisbandPartyCMSG.fromBnet(accountId, envelope.requestDisbandParty)
  }

  override fun handle(msg: RequestDisbandPartyCMSG): Boolean {
    try {
      val disbanded = partyService.disbandParty(msg.playerId, msg.partyId)
      rosterPublisher.disbanded(disbanded.partyId, disbanded.members)

      LOG.debug { "Player ${msg.playerId} disbanded party ${msg.partyId}" }
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

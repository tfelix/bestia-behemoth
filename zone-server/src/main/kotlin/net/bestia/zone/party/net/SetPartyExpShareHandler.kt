package net.bestia.zone.party.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

/** The owner picks how kill EXP is shared; every member sees the new mode in the roster. */
@Component
class SetPartyExpShareHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<SetPartyExpShareCMSG> {

  override val wire = decoder(MessageCase.SET_PARTY_EXP_SHARE) { accountId, envelope ->
    SetPartyExpShareCMSG.fromBnet(accountId, envelope.setPartyExpShare)
  }

  override fun handle(msg: SetPartyExpShareCMSG): Boolean {
    val expShare = msg.expShare
    if (expShare == null) {
      // The window offers the two modes and nothing else.
      LOG.warn { "Account ${msg.playerId} asked for an EXP mode this server does not know" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.REQUEST_REFUSED))
      return true
    }

    try {
      val partyId = partyService.setExpShare(msg.playerId, expShare)

      rosterPublisher.request(partyId)
    } catch (e: PartyException) {
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

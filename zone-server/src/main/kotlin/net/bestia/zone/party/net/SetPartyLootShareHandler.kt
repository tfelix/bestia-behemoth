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

/** The owner picks how the party holds its drops; every member sees the new mode in the roster. */
@Component
class SetPartyLootShareHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor
) : IoMessageHandler<SetPartyLootShareCMSG> {

  override val wire = decoder(MessageCase.SET_PARTY_LOOT_SHARE) { accountId, envelope ->
    SetPartyLootShareCMSG.fromBnet(accountId, envelope.setPartyLootShare)
  }

  override fun handle(msg: SetPartyLootShareCMSG): Boolean {
    val lootShare = msg.lootShare
    if (lootShare == null) {
      // The window offers the two modes and nothing else.
      LOG.warn { "Account ${msg.playerId} asked for a loot mode this server does not know" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.REQUEST_REFUSED))
      return true
    }

    try {
      val partyId = partyService.setLootShare(msg.playerId, lootShare)

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

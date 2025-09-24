package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.party.CreatePartyCMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.AlreadyInPartyException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CreatePartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<CreatePartyCMSG> {

  override val handles = CreatePartyCMSG::class

  override fun handle(msg: CreatePartyCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      val party = partyService.createParty(msg.playerId, msg.partyName)
      // Send party info back to the creator
      val partyInfo = partyService.getPartyInfo(party.id)

      if (partyInfo != null) {
        LOG.debug { "Player ${msg.playerId} created party '${msg.partyName}' with ID ${party.id}" }
        outMessageProcessor.sendToPlayer(msg.playerId, partyInfo)
      } else {
        outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.NO_PARTY))
      }
    } catch (_: AlreadyInPartyException) {
      LOG.debug { "Player ${msg.playerId} tried to create a party but is already in one." }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.ALREADY_IN_PARTY))
    } catch (_: DataIntegrityViolationException) {
      LOG.debug { "Player ${msg.playerId} tried to create a party but name ${msg.partyName} is already in use." }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.PARTY_NAME_IN_USE))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

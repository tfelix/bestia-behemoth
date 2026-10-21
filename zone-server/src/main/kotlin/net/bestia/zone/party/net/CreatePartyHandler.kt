package net.bestia.zone.party.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyRosterPublisher
import net.bestia.zone.party.PartyService
import org.springframework.stereotype.Component

@Component
class CreatePartyHandler(
  private val partyService: PartyService,
  private val rosterPublisher: PartyRosterPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val basicSkillGate: BasicSkillGate
) : IoMessageHandler<CreatePartyCMSG> {

  override val wire = decoder(MessageCase.CREATE_PARTY) { accountId, envelope ->
    CreatePartyCMSG.fromBnet(accountId, envelope.createParty)
  }

  override fun handle(msg: CreatePartyCMSG): Boolean {
    // Parties are Basic Skill rank 5. Its own code rather than a party one: the same denial has to read the same
    // whether it comes from here or from an invite.
    if (!basicSkillGate.mayParty(msg.playerId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_PARTY_LOCKED))
      return true
    }

    try {
      val party = partyService.createParty(msg.playerId, msg.partyName)
      LOG.debug { "Player ${msg.playerId} created party '${party.name}' with ID ${party.id}" }

      rosterPublisher.request(party.id)
    } catch (e: PartyException) {
      LOG.debug { "Player ${msg.playerId} could not create party '${msg.partyName}': ${e.message}" }
      outMessageProcessor.sendToPlayer(msg.playerId, e.toOperationError())
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

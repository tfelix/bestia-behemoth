package net.bestia.zone.party.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.skill.BasicSkillGate
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.party.CreatePartyCMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.PartyErrorSMSG
import net.bestia.zone.party.AlreadyInPartyException
import net.bestia.zone.party.InvalidPartyNameException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class CreatePartyHandler(
  private val partyService: PartyService,
  private val outMessageProcessor: OutMessageProcessor,
  private val basicSkillGate: BasicSkillGate
) : InMessageProcessor.IncomingMessageHandler<CreatePartyCMSG> {

  override val handles = CreatePartyCMSG::class

  override fun handle(msg: CreatePartyCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Parties are Basic Skill rank 5. Refused through the shared OperationError rather than a PartyErrorCode:
    // the reason has nothing to do with parties, and the same denial has to read the same whether it comes
    // from here or from an invite.
    if (!basicSkillGate.mayParty(msg.playerId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_PARTY_LOCKED))
      return true
    }

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
    } catch (_: InvalidPartyNameException) {
      LOG.debug { "Player ${msg.playerId} tried to create a party with invalid name '${msg.partyName}'." }
      outMessageProcessor.sendToPlayer(msg.playerId, PartyErrorSMSG(PartyErrorSMSG.PartyErrorCode.INVALID_PARTY_NAME))
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

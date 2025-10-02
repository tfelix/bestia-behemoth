package net.bestia.zone.message.processor

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.entity.AttackEntityCMSG
import net.bestia.zone.entity.GetAllEntitiesCMSG
import net.bestia.zone.message.*
import net.bestia.zone.system.ChatCMSG
import net.bestia.zone.system.PingCMSG
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Receives Bnet protobuf messages, converts them into the local message format and hands over the messages
 * to the regular message processing event system.
 */
@Component
class BnetMessageProcessorAdapter(
  private val inMessageProcessor: InMessageProcessor
) {
  @EventListener
  fun handleMessageEnvelopeReceived(event: MessageEnvelopeReceivedEvent) {
    val accountId = event.senderAccountId
    val envelope = event.envelope

    val internalMessage = when {
      envelope.hasGetMaster() -> GetMasterCMSG(accountId)
      envelope.hasGetSelf() -> GetSelfCMSG(accountId)
      envelope.hasPing() -> PingCMSG(accountId)
      envelope.hasChatCmsg() -> ChatCMSG.fromBnet(accountId, envelope.chatCmsg)
      envelope.hasSelectMaster() -> SelectMasterCMSG(accountId, envelope.selectMaster.masterId)
      envelope.hasSelectActiveEntity() -> SelectEntityCMSG(accountId, envelope.selectActiveEntity.entityId)
      // envelope.hasMoveActiveEntity() -> MoveActiveEntityCMSG.fromBnet(accountId, envelope.moveActiveEntity)
      envelope.hasGetAllEntities() -> GetAllEntitiesCMSG(accountId)
      envelope.hasAttackEntity() -> AttackEntityCMSG.fromBnet(accountId, envelope.attackEntity)

      else -> throw UnknownBnetMessageException(envelope)
    }

    LOG.trace { "handleMessageEnvelopeReceived: Received internal message $internalMessage" }

    inMessageProcessor.process(internalMessage)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
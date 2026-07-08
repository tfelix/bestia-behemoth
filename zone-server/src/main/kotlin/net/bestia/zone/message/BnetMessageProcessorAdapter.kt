package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.GetSelfCMSG
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.account.master.InvestSkillPointCMSG
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.battle.attack.AttackEntityCMSG
import net.bestia.zone.chat.ChatCMSG
import net.bestia.zone.entity.GetAllEntitiesCMSG
import net.bestia.zone.entity.SelectEntityCMSG
import net.bestia.zone.item.DropItemCMSG
import net.bestia.zone.item.GetInventoryCMSG
import net.bestia.zone.item.LootItemCMSG
import net.bestia.zone.item.UseItemCMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.UnknownBnetMessageException
import net.bestia.zone.socket.PingCMSG
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
      envelope.hasChatCmsg() -> ChatCMSG.Companion.fromBnet(accountId, envelope.chatCmsg)
      envelope.hasSelectMaster() -> SelectMasterCMSG(accountId, envelope.selectMaster.masterId)
      envelope.hasInvestSkillPoint() -> InvestSkillPointCMSG.Companion.fromBnet(accountId, envelope.investSkillPoint)
      envelope.hasSelectActiveEntity() -> SelectEntityCMSG(accountId, envelope.selectActiveEntity.entityId)
      // envelope.hasMoveActiveEntity() -> MoveActiveEntityCMSG.fromBnet(accountId, envelope.moveActiveEntity)
      envelope.hasGetAllEntities() -> GetAllEntitiesCMSG(accountId)
      envelope.hasAttackEntity() -> AttackEntityCMSG.Companion.fromBnet(accountId, envelope.attackEntity)
      envelope.hasGetInventory() -> GetInventoryCMSG(accountId)
      envelope.hasUseItem() -> UseItemCMSG.Companion.fromBnet(accountId, envelope.useItem)
      envelope.hasDropItem() -> DropItemCMSG.Companion.fromBnet(accountId, envelope.dropItem)
      envelope.hasLootItem() -> LootItemCMSG.Companion.fromBnet(accountId, envelope.lootItem)

      else -> throw UnknownBnetMessageException(envelope)
    }

    LOG.trace { "handleMessageEnvelopeReceived: Received internal message $internalMessage" }

    inMessageProcessor.process(internalMessage)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
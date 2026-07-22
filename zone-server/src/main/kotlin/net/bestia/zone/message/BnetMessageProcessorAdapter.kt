package net.bestia.zone.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.GetSelfCMSG
import net.bestia.zone.account.master.CreateMasterCMSG
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.account.master.skill.InvestSkillPointCMSG
import net.bestia.zone.account.master.status.InvestStatusPointCMSG
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.battle.ActivateSkillCMSG
import net.bestia.zone.battle.AttackEntityCMSG
import net.bestia.zone.skill.GetSkillsCMSG
import net.bestia.zone.chat.ChatCMSG
import net.bestia.zone.entity.GetAllEntitiesCMSG
import net.bestia.zone.entity.MoveActiveEntityCMSG
import net.bestia.zone.entity.SelectEntityCMSG
import net.bestia.zone.item.DropItemCMSG
import net.bestia.zone.item.equip.EquipItemCMSG
import net.bestia.zone.item.equip.UnequipItemCMSG
import net.bestia.zone.item.inventory.GetInventoryCMSG
import net.bestia.zone.item.loot.LootItemCMSG
import net.bestia.zone.item.UseItemCMSG
import net.bestia.zone.ecs.logout.RequestLogoutCMSG
import net.bestia.zone.party.AcceptPartyInviteCMSG
import net.bestia.zone.party.DeclinePartyInviteCMSG
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
      envelope.hasCreateMaster() -> CreateMasterCMSG.fromBnet(accountId, envelope.createMaster)
      envelope.hasInvestSkillPoint() -> InvestSkillPointCMSG.Companion.fromBnet(accountId, envelope.investSkillPoint)
      envelope.hasInvestStatusPoint() -> InvestStatusPointCMSG.Companion.fromBnet(accountId, envelope.investStatusPoint)
      envelope.hasGetSkills() -> GetSkillsCMSG(accountId)
      envelope.hasActivateSkill() -> ActivateSkillCMSG.Companion.fromBnet(accountId, envelope.activateSkill)
      envelope.hasSelectActiveEntity() -> SelectEntityCMSG(accountId, envelope.selectActiveEntity.entityId)
      envelope.hasMoveActiveEntity() -> MoveActiveEntityCMSG.Companion.fromBnet(accountId, envelope.moveActiveEntity)
      envelope.hasGetAllEntities() -> GetAllEntitiesCMSG(accountId)
      envelope.hasAttackEntity() -> AttackEntityCMSG.Companion.fromBnet(accountId, envelope.attackEntity)
      envelope.hasGetInventory() -> GetInventoryCMSG(accountId)
      envelope.hasUseItem() -> UseItemCMSG.Companion.fromBnet(accountId, envelope.useItem)
      envelope.hasDropItem() -> DropItemCMSG.Companion.fromBnet(accountId, envelope.dropItem)
      envelope.hasLootItem() -> LootItemCMSG.Companion.fromBnet(accountId, envelope.lootItem)
      envelope.hasEquipItem() -> EquipItemCMSG.Companion.fromBnet(accountId, envelope.equipItem)
      envelope.hasUnequipItem() -> UnequipItemCMSG.Companion.fromBnet(accountId, envelope.unequipItem)
      envelope.hasRequestLogout() -> RequestLogoutCMSG.Companion.fromBnet(accountId, envelope.requestLogout)
      envelope.hasAcceptPartyInvite() -> AcceptPartyInviteCMSG.fromBnet(accountId, envelope.acceptPartyInvite)
      envelope.hasDeclinePartyInvite() -> DeclinePartyInviteCMSG.fromBnet(accountId, envelope.declinePartyInvite)

      else -> throw UnknownBnetMessageException(envelope)
    }

    // A `fromBnet` may return null when the payload is well-formed protobuf but semantically
    // invalid (e.g. an equip slot ordinal this server version does not know). That is a misbehaving
    // client, not a server bug - drop the message instead of tearing the connection down.
    if (internalMessage == null) {
      LOG.warn { "handleMessageEnvelopeReceived: dropping unparsable message from account $accountId" }
      return
    }

    LOG.trace { "handleMessageEnvelopeReceived: Received internal message $internalMessage" }

    inMessageProcessor.process(internalMessage)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
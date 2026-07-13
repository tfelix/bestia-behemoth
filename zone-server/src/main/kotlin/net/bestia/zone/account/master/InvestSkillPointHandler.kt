package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.MasterSkillListBuilder
import net.bestia.zone.battle.skill.MasterSkillTreeService
import net.bestia.zone.battle.skill.SkillListSMSG
import net.bestia.zone.battle.skill.SkillPointInvestment
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a client request to spend one or more skill points across one or more nodes of the
 * master's skill tree in a single batch.
 */
@Component
class InvestSkillPointHandler(
  private val masterSkillTreeService: MasterSkillTreeService,
  private val masterResolver: MasterResolver,
  private val masterSkillListBuilder: MasterSkillListBuilder,
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<InvestSkillPointCMSG> {
  override val handles = InvestSkillPointCMSG::class

  override fun handle(msg: InvestSkillPointCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val master = masterResolver.getSelectedMasterByAccountId(msg.playerId)
    val investments = msg.investedPoints.map { SkillPointInvestment(it.attackId, it.amount) }
    masterSkillTreeService.investSkillPoints(master.id, investments)

    // The merged skill list isn't part of the ECS dirty-sync pipeline (see GetSkillsHandler),
    // so nothing else pushes the client a refresh after this - push one now rather than making
    // the client guess how long the investment took to process and poll for it.
    val masterEntityId = masterResolver.getSelectedMasterEntityIdByAccountId(msg.playerId)
    if (masterEntityId != null) {
      val entries = masterSkillListBuilder.buildMasterSkillEntries(master.id)
      outMessageProcessor.sendToPlayer(msg.playerId, SkillListSMSG(masterEntityId, entries))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

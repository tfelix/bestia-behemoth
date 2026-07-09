package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.MasterSkillTreeService
import net.bestia.zone.battle.skill.SkillPointInvestment
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Handles a client request to spend one or more skill points across one or more nodes of the
 * master's skill tree in a single batch.
 */
@Component
class InvestSkillPointHandler(
  private val masterSkillTreeService: MasterSkillTreeService,
  private val masterResolver: MasterResolver
) : InMessageProcessor.IncomingMessageHandler<InvestSkillPointCMSG> {
  override val handles = InvestSkillPointCMSG::class

  override fun handle(msg: InvestSkillPointCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val master = masterResolver.getSelectedMasterByAccountId(msg.playerId)
    val investments = msg.investedPoints.map { SkillPointInvestment(it.attackId, it.amount) }
    masterSkillTreeService.investSkillPoints(master.id, investments)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

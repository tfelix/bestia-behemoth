package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.master.skill.MasterSkillListBuilder
import net.bestia.zone.master.skill.MasterSkillTreeService
import net.bestia.zone.master.skill.SkillListSMSG
import net.bestia.zone.master.skill.SkillPointInvestment

/**
 * Handles a client request to spend one or more skill points across one or more nodes of the
 * master's skill tree in a single batch.
 */
@Component
class InvestSkillPointHandler(
  private val masterSkillTreeService: MasterSkillTreeService,
  private val connectionInfoService: ConnectionInfoService,
  private val masterSkillListBuilder: MasterSkillListBuilder,
  private val outMessageProcessor: OutMessageProcessor
) : TickMessageHandler<InvestSkillPointCMSG> {
  override val wire = decoder(MessageCase.INVEST_SKILL_POINT) { accountId, envelope ->
    InvestSkillPointCMSG.fromBnet(accountId, envelope.investSkillPoint)
  }

  override fun handle(world: World, msg: InvestSkillPointCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)
    val investments = msg.investedPoints.map { SkillPointInvestment(it.attackId, it.amount) }
    masterSkillTreeService.investSkillPoints(world, msg.playerId, masterEntityId, investments)

    // The merged skill list isn't part of the ECS dirty-sync pipeline (see GetSkillsHandler), so nothing
    // else pushes the client a refresh after this.
    val levels = world.get(masterEntityId, KnownSkills::class)?.levels().orEmpty()
    outMessageProcessor.sendToPlayer(msg.playerId, SkillListSMSG(masterEntityId, masterSkillListBuilder.entriesFor(levels)))

    return true
  }
}

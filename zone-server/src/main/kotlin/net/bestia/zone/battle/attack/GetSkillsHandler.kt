package net.bestia.zone.battle.attack

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.component.SkillListSMSG
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.EntityNotOwnedSessionException
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves the merged skill list (regular/fixed catalog + individually learned) for the
 * account's currently active entity, and sends it back directly rather than relying on the
 * ECS dirty-sync pipeline, since the merged shape isn't backed by a single component.
 */
@Component
class GetSkillsHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry
) : InMessageProcessor.IncomingMessageHandler<GetSkillsCMSG> {
  override val handles = GetSkillsCMSG::class

  @Transactional(readOnly = true)
  override fun handle(msg: GetSkillsCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    val entries = if (activeEntityId == masterEntityId) {
      buildMasterSkillEntries(masterId)
    } else {
      val ownedEntity = connectionInfoService.getOwnedEntitiesByMaster(msg.playerId, masterId)
        .firstOrNull { it.entityId == activeEntityId }
        ?: throw EntityNotOwnedSessionException(msg.playerId, activeEntityId)

      buildBestiaSkillEntries(ownedEntity.playerBestiaId)
    }

    outMessageProcessor.sendToPlayer(msg.playerId, SkillListSMSG(activeEntityId, entries))

    return true
  }

  /**
   * A master's regular catalog is its whole skill tree (config, not player state) — every node
   * is shown, dimmed on the client until points are invested into it.
   */
  private fun buildMasterSkillEntries(masterId: Long): List<SkillListSMSG.SkillListEntry> {
    masterRepository.findByIdOrThrow(masterId)

    val learnedBySkillId = learnedSkillRepository.findAllByMasterId(masterId)
      .associateBy { it.skill.id }

    return masterSkillTreeRegistry.all().map { node ->
      val investedLevel = learnedBySkillId[node.skillId]?.level ?: 0
      SkillListSMSG.SkillListEntry(
        attackId = node.skillId,
        level = investedLevel,
        maxLevel = node.maxLevel,
        learned = investedLevel > 0
      )
    }
  }

  /**
   * A bestia's regular catalog is its species' fixed, level-gated skill table; on top of that it
   * may have individually learned custom skills (item-taught).
   */
  private fun buildBestiaSkillEntries(playerBestiaId: Long): List<SkillListSMSG.SkillListEntry> {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    val fixedEntries = playerBestia.bestia.skills.map { bestiaSkill ->
      val learned = playerBestia.level >= bestiaSkill.requiredLevel
      SkillListSMSG.SkillListEntry(
        attackId = bestiaSkill.skill.id,
        level = if (learned) 1 else 0,
        maxLevel = 1,
        learned = learned
      )
    }
    val fixedSkillIds = fixedEntries.map { it.attackId }.toSet()

    val customEntries = learnedSkillRepository.findAllByPlayerBestiaId(playerBestiaId)
      .filter { it.skill.id !in fixedSkillIds }
      .map { learnedSkill ->
        SkillListSMSG.SkillListEntry(
          attackId = learnedSkill.skill.id,
          level = learnedSkill.level,
          maxLevel = learnedSkill.level,
          learned = true
        )
      }

    return fixedEntries + customEntries
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

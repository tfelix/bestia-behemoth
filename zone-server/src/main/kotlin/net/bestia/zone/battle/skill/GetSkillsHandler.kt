package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.bestia.PlayerBestiaNotFoundException
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.EntityNotOwnedSessionException
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
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
      buildBestiaSkillEntries(msg.playerId, masterId, activeEntityId)
    }

    outMessageProcessor.sendToPlayer(msg.playerId, SkillListSMSG(activeEntityId, entries))

    return true
  }

  /**
   * A master's regular catalog is its whole skill tree (config, not player state) — every node
   * is shown, dimmed on the client until points are invested into it.
   */
  private fun buildMasterSkillEntries(masterId: Long): List<SkillListSMSG.SkillListEntry> {
    // safety check if the master exists we can not see this alone by selecting all learned skills
    masterRepository.findByIdOrThrow(masterId)

    val learnedBySkillId = learnedSkillRepository.findAllByMasterId(masterId)
      .associateBy { it.skill.id }

    return masterSkillTreeRegistry.all().map { node ->
      val investedLevel = learnedBySkillId[node.skillId]?.level ?: 0
      SkillListSMSG.SkillListEntry(
        skillId = node.skillId,
        level = investedLevel,
      )
    }
  }

  /**
   * A bestia's regular catalog is its species' fixed, level-gated skill table; on top of that it
   * may have individually learned custom skills (item-taught).
   */
  private fun buildBestiaSkillEntries(
    accountId: AccountId,
    masterId: Long,
    activeEntityId: EntityId
  ): List<SkillListSMSG.SkillListEntry> {
    val ownedEntities = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
    val playerBestiaId = ownedEntities.firstOrNull { it.entityId == activeEntityId }?.playerBestiaId

    if (playerBestiaId == null) {
      throw EntityNotOwnedSessionException(accountId, activeEntityId)
    }

    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    val fixedEntries = playerBestia.bestia.skills.map { bestiaSkill ->
      val learned = playerBestia.level >= bestiaSkill.requiredLevel
      SkillListSMSG.SkillListEntry(
        skillId = bestiaSkill.skill.id,
        level = if (learned) 1 else 0,
      )
    }
    val fixedSkillIds = fixedEntries.map { it.skillId }.toSet()

    val customEntries = learnedSkillRepository.findAllByPlayerBestiaId(playerBestiaId)
      .filter { it.skill.id !in fixedSkillIds }
      .map { learnedSkill ->
        SkillListSMSG.SkillListEntry(
          skillId = learnedSkill.skill.id,
          level = learnedSkill.level,
        )
      }

    return fixedEntries + customEntries
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

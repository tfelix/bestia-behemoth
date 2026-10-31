package net.bestia.zone.master.skill

import net.bestia.zone.bestia.BestiaSkillLevel
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.EntityNotOwnedSessionException
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves the skill list of an account's active entity: the master's tree, or a bestia's species table merged
 * with its custom skills. Not part of the ECS dirty-sync pipeline, since the merged shape isn't backed by a single
 * component, so whoever changes the skills pushes the result itself.
 */
@Service
class SkillListService(
  private val connectionInfoService: ConnectionInfoService,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val masterSkillListBuilder: MasterSkillListBuilder,
  private val world: WorldView,
) {

  @Transactional(readOnly = true)
  fun entriesFor(accountId: AccountId, activeEntityId: EntityId): List<SkillListSMSG.SkillListEntry> {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

    if (activeEntityId == masterEntityId) {
      val levels = world.read { get(masterEntityId, KnownSkills::class)?.levels() }.orEmpty()

      return masterSkillListBuilder.entriesFor(levels)
    }

    return bestiaEntries(accountId, connectionInfoService.getMasterId(accountId), activeEntityId)
  }

  /**
   * A bestia's regular catalog is its species' fixed, level-gated skill table; on top of that it may have
   * individually learned custom skills (item-taught, or a GM's `/allskills`).
   */
  private fun bestiaEntries(
    accountId: AccountId,
    masterId: Long,
    activeEntityId: EntityId
  ): List<SkillListSMSG.SkillListEntry> {
    val ownedEntities = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
    val playerBestiaId = ownedEntities.firstOrNull { it.entityId == activeEntityId }?.playerBestiaId
      ?: throw EntityNotOwnedSessionException(accountId, activeEntityId)

    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)
    val customLevels = learnedSkillRepository.findAllByPlayerBestiaId(playerBestiaId)
      .associate { it.skill.id to it.level }

    // A custom row teaches a species skill ahead of its level, so the two sources are merged, not either/or.
    val fixedEntries = playerBestia.bestia.skills.map { bestiaSkill ->
      val skillId = bestiaSkill.skill.id
      val levelByTable =
        if (playerBestia.level >= bestiaSkill.requiredLevel) BestiaSkillLevel.forLearnLevel(bestiaSkill.requiredLevel) else 0

      SkillListSMSG.SkillListEntry(
        skillId = skillId,
        level = maxOf(levelByTable, customLevels[skillId] ?: 0),
      )
    }
    val fixedSkillIds = fixedEntries.map { it.skillId }.toSet()

    val customEntries = customLevels
      .filterKeys { it !in fixedSkillIds }
      .map { (skillId, level) -> SkillListSMSG.SkillListEntry(skillId = skillId, level = level) }

    return fixedEntries + customEntries
  }
}

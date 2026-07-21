package net.bestia.zone.skill

import net.bestia.zone.battle.skill.InsufficientLevelException
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.ecs.battle.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Lets a captured bestia learn a custom skill on top of its species' fixed level-up table
 * ([net.bestia.zone.bestia.BestiaSkill]). This is content-driven: intended to be called by
 * whatever grants the skill (e.g. an item-use effect), not directly by a player action.
 */
@Service
class BestiaSkillLearnService(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val skillRepository: SkillRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService
) {

  @Transactional
  fun learnCustomSkill(
    playerBestiaId: PlayerBestiaId,
    skillId: Long,
  ): LearnedSkill {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)
    val skillToLearn = skillRepository.findByIdOrThrow(skillId)

    if (playerBestia.level < skillToLearn.requiredLevel) {
      throw InsufficientLevelException(skillToLearn.requiredLevel, playerBestia.level)
    }

    if (learnedSkillRepository.findByPlayerBestiaIdAndSkillId(playerBestiaId, skillId) != null) {
      throw SkillAlreadyLearnedException(skillId)
    }

    val learnedSkill = learnedSkillRepository.save(
      LearnedSkill(
        skill = skillToLearn,
        playerBestia = playerBestia,
      )
    )

    val accountId = playerBestia.master.account.id
    val masterId = playerBestia.master.id
    val entityId = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .find { it.playerBestiaId == playerBestiaId }
      ?.entityId

    if (entityId != null) {
      world.modify(entityId) { entityId ->
        // AvailableSkills is internal (not client-synced), so no dirty flag is involved.
        get(entityId, KnownSkills::class)?.learnOrUpdate(skillId)
      }
    }

    return learnedSkill
  }
}
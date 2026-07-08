package net.bestia.zone.battle.attack

import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Lets a captured bestia learn a custom skill on top of its species' fixed level-up table
 * ([net.bestia.zone.bestia.BestiaSkill]). This is content-driven: intended to be called by
 * whatever grants the skill (e.g. an item-use effect), not directly by a player action.
 */
@Service
class BestiaSkillLearningService(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val skillRepository: SkillRepository,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val world: World,
  private val connectionInfoService: ConnectionInfoService
) {

  /**
   * Teaches [playerBestiaId] the skill [skillId] at [skillLevel], provided the bestia has
   * reached [requiredLevel]. Throws [InsufficientLevelException] if the level requirement isn't
   * met, or [SkillAlreadyLearnedException] if the skill is already known.
   */
  @Transactional
  fun learnCustomSkill(
    playerBestiaId: PlayerBestiaId,
    skillId: Long,
    requiredLevel: Int,
    skillLevel: Int = 1
  ): LearnedSkill {
    val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)

    if (playerBestia.level < requiredLevel) {
      throw InsufficientLevelException(requiredLevel, playerBestia.level)
    }

    if (learnedSkillRepository.findByPlayerBestiaIdAndSkillId(playerBestiaId, skillId) != null) {
      throw SkillAlreadyLearnedException(skillId)
    }

    val skill = skillRepository.findByIdOrThrow(skillId)
    val learned = learnedSkillRepository.save(
      LearnedSkill(skill = skill, playerBestia = playerBestia).apply { level = skillLevel }
    )

    val accountId = playerBestia.master.account.id
    val masterId = playerBestia.master.id
    val entityId = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .find { it.playerBestiaId == playerBestiaId }
      ?.entityId

    if (entityId != null) {
      world.modify(entityId) { id ->
        // FIXME properly learn the attack. also inform the owner via a skill messag.e
        // world.get(id, AvailableAttacks::class)?.learnOrUpdate(skillId, skillLevel)
      }
    }

    return learned
  }
}

package net.bestia.zone.battle.attack

import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.status.SkillPoints
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Spends a bestia master's unspent skill points to invest levels into nodes of the master skill
 * tree ([MasterSkillTreeRegistry]). The tree is a DAG: a node only becomes investable once all of
 * its [MasterSkillPrerequisite] edges are satisfied at the required level.
 */
@Service
class MasterSkillTreeService(
  private val masterRepository: MasterRepository,
  private val skillRepository: SkillRepository,
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry,
  private val learnedSkillRepository: LearnedSkillRepository,
  private val world: World,
  private val masterResolver: MasterResolver
) {

  /**
   * Applies every investment in [investments] in order, within a single transaction: an earlier
   * entry can satisfy the prerequisite of a later one in the same request. If any entry can't be
   * applied (no points left, max level reached, prerequisite unmet, ...) the whole batch is
   * rolled back.
   */
  @Transactional
  fun investSkillPoints(masterId: Long, investments: List<SkillPointInvestment>): List<LearnedSkill> {
    val master = masterRepository.findByIdOrThrow(masterId)
    val updatedSkills = LinkedHashMap<Long, LearnedSkill>()

    for (investment in investments) {
      repeat(investment.amount) {
        updatedSkills[investment.skillId] = investSingleLevel(master, investment.skillId)
      }
    }

    masterRepository.save(master)
    syncToEcs(master, updatedSkills.values)

    return updatedSkills.values.toList()
  }

  private fun investSingleLevel(master: Master, skillId: Long): LearnedSkill {
    if (master.skillPoints <= 0) {
      throw NoSkillPointsAvailableException(master.id)
    }

    val node = masterSkillTreeRegistry.findBySkillId(skillId)
      ?: throw SkillTreeNodeNotFoundException(skillId)

    val existing = learnedSkillRepository.findByMasterIdAndSkillId(master.id, skillId)
    val currentLevel = existing?.level ?: 0

    if (currentLevel >= node.maxLevel) {
      throw SkillMaxLevelReachedException(skillId, node.maxLevel)
    }

    for (prerequisite in node.prerequisites) {
      val prerequisiteLevel = learnedSkillRepository
        .findByMasterIdAndSkillId(master.id, prerequisite.prerequisiteSkillId)?.level ?: 0

      if (prerequisiteLevel < prerequisite.requiredLevel) {
        throw SkillPrerequisiteNotMetException(
          skillId = skillId,
          prerequisiteSkillId = prerequisite.prerequisiteSkillId,
          requiredLevel = prerequisite.requiredLevel,
          currentLevel = prerequisiteLevel
        )
      }
    }

    val skill = skillRepository.findByIdOrThrow(skillId)
    val learnedSkill = existing ?: LearnedSkill(skill = skill, master = master)
    learnedSkill.level = currentLevel + 1
    learnedSkillRepository.save(learnedSkill)

    master.skillPoints -= 1

    return learnedSkill
  }

  private fun syncToEcs(master: Master, updatedSkills: Collection<LearnedSkill>) {
    if (updatedSkills.isEmpty()) return

    val entityId = masterResolver.getEntityIdByMasterId(master.id) ?: return

    world.modify(entityId) { id ->
      updatedSkills.forEach { learnedSkill ->
        world.get(id, AvailableAttacks::class)?.learnOrUpdate(learnedSkill.skill.id, learnedSkill.level)
      }
      world.get(id, SkillPoints::class)?.let { it.value = master.skillPoints }
      world.markChanged(id, SkillPoints::class)
    }
  }
}

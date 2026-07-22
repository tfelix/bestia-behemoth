package net.bestia.zone.account.master.skill

import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.skill.LearnedSkill
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.skill.NoSkillPointsAvailableException
import net.bestia.zone.skill.SkillMaxLevelReachedException
import net.bestia.zone.skill.SkillPrerequisiteNotMetException
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.skill.findByIdOrThrow
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
  private val world: WorldView,
  private val masterResolver: MasterResolver
) {

  /**
   * Applies every investment in [investments] in order, within a single transaction: an earlier
   * entry can satisfy the prerequisite of a later one in the same request. If any entry can't be
   * applied (no points left, max level reached, prerequisite unmet, ...) the whole batch is
   * rolled back.
   *
   * Unspent points are read from the live [SkillPoints] ECS component (the up-to-date source of
   * truth while the master is online) but the spend is now made durable atomically: the same
   * transaction that saves the levelled [LearnedSkill] rows also writes the decremented balance to
   * [Master.skillPoints], and the ECS component is only decremented **after commit** (see
   * [syncToEcs] registered as an after-commit callback). This mirrors how level-up exp persists
   * synchronously before the gained points become spendable, and closes the window where a crash
   * between commit and the periodic snapshot would refund an already-spent point. Requires an
   * active entity for the master; there is no offline spending path.
   */
  @Transactional
  fun investSkillPoints(masterId: Long, investments: List<SkillPointInvestment>): List<LearnedSkill> {
    val master = masterRepository.findByIdOrThrow(masterId)
    val entityId = masterResolver.getEntityIdByMasterId(masterId)
      ?: throw MasterNotFoundException()

    var remainingSkillPoints = world.read { get(entityId, SkillPoints::class)?.value } ?: 0
    val updatedSkills = LinkedHashMap<Long, LearnedSkill>()
    var spentSkillPoints = 0

    for (investment in investments) {
      repeat(investment.amount) {
        if (remainingSkillPoints <= 0) {
          throw NoSkillPointsAvailableException(master.id)
        }

        updatedSkills[investment.skillId] = investSingleLevel(master, investment.skillId)
        remainingSkillPoints -= 1
        spentSkillPoints += 1
      }
    }

    // Persist the point deduction in the SAME transaction as the LearnedSkill rows so the skill
    // level and the spent point can never diverge across a crash. Master.skillPoints is re-seeded
    // into the SkillPoints component on the next spawn (MasterEntityFactory), so it must be correct
    // even if the process dies before the periodic ECS snapshot runs.
    master.skillPoints = remainingSkillPoints
    masterRepository.save(master)

    // Only touch the (non-transactional) ECS world once the DB spend is durably committed, so a
    // rollback can never leave the in-memory component ahead of the persisted balance.
    afterCommit { syncToEcs(entityId, updatedSkills.values, spentSkillPoints) }

    return updatedSkills.values.toList()
  }

  /**
   * Runs [block] after the current transaction commits, or immediately if there is no active
   * transaction synchronization (e.g. in a unit test calling the service outside a transaction).
   */
  private fun afterCommit(block: () -> Unit) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() = block()
      })
    } else {
      block()
    }
  }

  /**
   * Validates and persists a single level-up of [skillId] for [master]. Does not touch the ECS
   * world - [investSkillPoints] applies every world-visible effect of the whole batch (points
   * spent, skills learned) in one go via [syncToEcs] once all entries have been validated.
   */
  private fun investSingleLevel(master: Master, skillId: Long): LearnedSkill {
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
          skillIdentifier = skillRepository.findByIdOrThrow(skillId).identifier,
          prerequisiteSkillIdentifier = skillRepository.findByIdOrThrow(prerequisite.prerequisiteSkillId).identifier,
          requiredLevel = prerequisite.requiredLevel,
          currentLevel = prerequisiteLevel
        )
      }
    }

    val skill = skillRepository.findByIdOrThrow(skillId)
    val learnedSkill = existing ?: LearnedSkill(skill = skill, master = master)
    learnedSkill.level = currentLevel + 1
    learnedSkillRepository.save(learnedSkill)

    return learnedSkill
  }

  /** The single place [investSkillPoints] mutates the ECS world, once per batch. */
  private fun syncToEcs(entityId: EntityId, updatedSkills: Collection<LearnedSkill>, spentSkillPoints: Int) {
    if (updatedSkills.isEmpty()) return

    world.modify(entityId) { id ->
      updatedSkills.forEach { learnedSkill ->
        get(id, KnownSkills::class)?.learnOrUpdate(learnedSkill.skill.id, learnedSkill.level)
      }
      if (spentSkillPoints > 0) {
        get(id, SkillPoints::class)?.let { it.value -= spentSkillPoints }
      }
    }
  }
}

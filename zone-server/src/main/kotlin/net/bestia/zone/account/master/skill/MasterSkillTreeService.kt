package net.bestia.zone.account.master.skill

import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.core.World
import net.bestia.zone.persistence.EntityWriteBehind
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.skill.BasicSkillTooLowForTreeException
import net.bestia.zone.skill.NoSkillPointsAvailableException
import net.bestia.zone.skill.SkillMaxLevelReachedException
import net.bestia.zone.skill.SkillPrerequisiteNotMetException
import net.bestia.zone.skill.SkillSubTreeNotUnlockedException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import net.bestia.zone.skill.MasterSkillsChangedEvent
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.skill.tree.SkillTreeNodeNotFoundException

/**
 * Spends a bestia master's unspent skill points to invest levels into nodes of the master skill
 * tree ([MasterSkillTreeRegistry]). The tree is a DAG: a node only becomes investable once all of
 * its [net.bestia.zone.skill.tree.MasterSkillPrerequisite] edges are satisfied at the required level.
 *
 * On the tick, against [KnownSkills] and [SkillPoints], which are the authority while the master is
 * online. The master's write-behind then saves the points and the learned levels in one transaction, so a
 * crash loses a spend together with what it bought, never one without the other.
 */
@Service
class MasterSkillTreeService(
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry,
  private val writeBehind: EntityWriteBehind,
  private val accountTasks: AccountTaskExecutor,
  private val events: ApplicationEventPublisher
) {

  /**
   * Applies every investment in [investments] in order: an earlier entry can satisfy the prerequisite of a
   * later one in the same request. The whole batch is checked before anything changes, so if any entry
   * cannot be applied (no points left, max level reached, prerequisite unmet, ...) nothing is.
   *
   * @return every skill that moved, with its new level
   */
  fun investSkillPoints(
    world: World,
    accountId: AccountId,
    entityId: EntityId,
    investments: List<SkillPointInvestment>
  ): Map<Long, Int> {
    val masterId = world.get(entityId, MasterComponent::class)?.masterId ?: throw MasterNotFoundException()
    val known = world.get(entityId, KnownSkills::class) ?: throw MasterNotFoundException()
    val points = world.get(entityId, SkillPoints::class) ?: throw MasterNotFoundException()

    val levels = HashMap(known.levels())
    val changed = LinkedHashMap<Long, Int>()
    var remaining = points.value

    for (investment in investments) {
      repeat(investment.amount) {
        if (remaining <= 0) {
          throw NoSkillPointsAvailableException(masterId)
        }

        val level = nextLevel(masterId, levels, investment.skillId)
        levels[investment.skillId] = level
        changed[investment.skillId] = level
        remaining -= 1
      }
    }

    if (changed.isEmpty()) return changed

    changed.forEach { (skillId, level) -> known.learnOrUpdate(skillId, level) }
    points.value = remaining
    // A newly learned or levelled passive feeds the status recalc, so the effective values are now stale.
    world.add(entityId, IsStatusValueDirty)

    writeBehind.persist(world, listOf(entityId), withStatusEffects = false)
    // Off the tick: the listener takes off gear the master outgrew, and that reads the database.
    accountTasks.onIo(accountId) { events.publishEvent(MasterSkillsChangedEvent(this, masterId, entityId)) }

    return changed
  }

  /**
   * Checks one more level of [skillId] against [levels], the master's levels as this batch has left them so
   * far, and returns the level it would reach.
   *
   * Basic Skill is read per level rather than once per batch, so one request can take it to 5 and then spend
   * into another tree - the same way an earlier entry can satisfy a later entry's prerequisite.
   */
  private fun nextLevel(masterId: Long, levels: Map<Long, Int>, skillId: Long): Int {
    val node = masterSkillTreeRegistry.findBySkillId(skillId)
      ?: throw SkillTreeNodeNotFoundException(skillId)

    if (node.tree != MasterSkillTreeRegistry.NOVICE_TREE) {
      // A tree without Basic Skill reads as level 0: failing open there would hand every tree to every novice.
      val basicSkillLevel = masterSkillTreeRegistry.basicSkillId?.let { levels[it] } ?: 0
      if (basicSkillLevel < TREE_UNLOCK_BASIC_SKILL_LEVEL) {
        throw BasicSkillTooLowForTreeException(
          masterId = masterId,
          tree = node.tree,
          requiredLevel = TREE_UNLOCK_BASIC_SKILL_LEVEL,
          currentLevel = basicSkillLevel
        )
      }
    }

    // Only gated when the tree actually has trunk skills to spend those points on first. Scholar
    // and Warrior today are nothing but a sub-tree (Priest/Wizard) with no trunk yet - unlike
    // Blacksmith/Artificer/Alchemist/Forester/Prospector/Miner, `master_skill_tree.yml` never
    // comments them "(unlocked at 5+ pts in ... Tree)", and gating them the same way would make
    // Priest/Wizard permanently unreachable rather than just unfinished.
    if (node.subTree != null && hasTrunkSkills(node.tree)) {
      val treePoints = pointsInvestedInTree(levels, node.tree)
      if (treePoints < SUB_TREE_UNLOCK_THRESHOLD) {
        throw SkillSubTreeNotUnlockedException(node.subTree, node.tree, SUB_TREE_UNLOCK_THRESHOLD, treePoints)
      }
    }

    val currentLevel = levels[skillId] ?: 0
    if (currentLevel >= node.maxLevel) {
      throw SkillMaxLevelReachedException(skillId, node.maxLevel)
    }

    for (prerequisite in node.prerequisites) {
      val prerequisiteLevel = levels[prerequisite.prerequisiteSkillId] ?: 0

      if (prerequisiteLevel < prerequisite.requiredLevel) {
        throw SkillPrerequisiteNotMetException(
          skillIdentifier = node.identifier,
          prerequisiteSkillIdentifier = masterSkillTreeRegistry.findBySkillId(prerequisite.prerequisiteSkillId)
            ?.identifier ?: "#${prerequisite.prerequisiteSkillId}",
          requiredLevel = prerequisite.requiredLevel,
          currentLevel = prerequisiteLevel
        )
      }
    }

    return currentLevel + 1
  }

  /**
   * Sums invested levels across every node sharing [tree] - trunk skills and every sub-tree under
   * it both count, matching "spend at least 5 skill points into the Craftsman tree" rather than
   * "into the Craftsman trunk specifically."
   */
  private fun pointsInvestedInTree(levels: Map<Long, Int>, tree: String): Int {
    return levels.entries.sumOf { (skillId, level) ->
      if (masterSkillTreeRegistry.findBySkillId(skillId)?.tree == tree) level else 0
    }
  }

  private fun hasTrunkSkills(tree: String): Boolean {
    return masterSkillTreeRegistry.all().any { it.tree == tree && it.subTree == null }
  }

  companion object {

    /**
     * How far Basic Skill must be taken before any tree but Novice opens. Deliberately its own
     * constant rather than a reuse of [SUB_TREE_UNLOCK_THRESHOLD] or `BasicSkillGate.PARTY_RANK`:
     * three rules that happen to share a number today.
     */
    private const val TREE_UNLOCK_BASIC_SKILL_LEVEL = 5

    /** How many points must be spent anywhere in a tree before any of its sub-trees can be. */
    private const val SUB_TREE_UNLOCK_THRESHOLD = 5
  }
}

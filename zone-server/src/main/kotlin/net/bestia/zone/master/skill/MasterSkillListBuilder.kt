package net.bestia.zone.master.skill

import net.bestia.zone.casting.plagiarism.PlagiarizedSkill
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry

/**
 * Builds a master's merged skill list - the whole skill tree (config, not player state), every
 * node shown and dimmed on the client until points are invested into it. Shared by
 * [net.bestia.zone.master.net.GetSkillsHandler] (client-requested refresh) and [net.bestia.zone.master.net.InvestSkillPointHandler] (proactive push
 * right after an investment).
 */
@Service
class MasterSkillListBuilder(
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry
) {

  /**
   * [levels] come from the master's `KnownSkills`: the database may not have the latest spend yet. A spell copied
   * with Plagiarism, [copied], is listed after the tree as a temporary entry.
   */
  fun entriesFor(levels: Map<Long, Int>, copied: PlagiarizedSkill? = null): List<SkillListSMSG.SkillListEntry> {
    val tree = masterSkillTreeRegistry.all().map { node ->
      SkillListSMSG.SkillListEntry(
        skillId = node.skillId,
        level = levels[node.skillId] ?: 0,
      )
    }
    val copy = copied?.let { SkillListSMSG.SkillListEntry(it.skillId, it.level, temporary = true) }

    return tree + listOfNotNull(copy)
  }

  /** The list of the master [masterEntityId] as it stands in [world]. */
  fun entriesFor(world: World, masterEntityId: EntityId): List<SkillListSMSG.SkillListEntry> {
    val levels = world.get(masterEntityId, KnownSkills::class)?.levels().orEmpty()

    return entriesFor(levels, world.get(masterEntityId, PlagiarizedSkill::class))
  }
}

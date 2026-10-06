package net.bestia.zone.master.skill

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

  /** [levels] come from the master's `KnownSkills`: the database may not have the latest spend yet. */
  fun entriesFor(levels: Map<Long, Int>): List<SkillListSMSG.SkillListEntry> {
    return masterSkillTreeRegistry.all().map { node ->
      SkillListSMSG.SkillListEntry(
        skillId = node.skillId,
        level = levels[node.skillId] ?: 0,
      )
    }
  }
}

package net.bestia.zone.battle.attack

import org.springframework.stereotype.Service

/**
 * In-memory store of the master skill tree, keyed by skill id. Populated once at boot by
 * [net.bestia.zone.boot.MasterSkillTreeImporterBootRunner] from `master_skill_tree.yml`; the tree
 * shape is config, not player state, so it is never persisted to the database.
 */
@Service
class MasterSkillTreeRegistry {

  private var nodesBySkillId: Map<Long, MasterSkillTreeNode> = emptyMap()

  fun load(nodes: List<MasterSkillTreeNode>) {
    nodesBySkillId = nodes.associateBy { it.skillId }
  }

  fun findBySkillId(skillId: Long): MasterSkillTreeNode? = nodesBySkillId[skillId]

  fun all(): Collection<MasterSkillTreeNode> = nodesBySkillId.values
}

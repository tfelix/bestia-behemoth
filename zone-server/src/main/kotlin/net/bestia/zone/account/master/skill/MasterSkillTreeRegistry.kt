package net.bestia.zone.account.master.skill

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

  fun findBySkillId(skillId: Long): MasterSkillTreeNode? {
    return nodesBySkillId[skillId]
  }

  fun all(): Collection<MasterSkillTreeNode> {
    return nodesBySkillId.values
  }

  /**
   * Every node outside the Novice tree - what "has this master specialised yet" is asked of, by
   * [NoviceGate] and anything else that needs the question.
   *
   * Phrased as the complement rather than as `findByTree(NOVICE_TREE)` because the callers all want the
   * negative: a master is a novice for as long as *none* of these has a level in it.
   */
  fun nonNoviceNodes(): Collection<MasterSkillTreeNode> {
    return nodesBySkillId.values.filter { it.tree != NOVICE_TREE }
  }

  companion object {

    /**
     * The one tree a master may invest in from the start, and the one whose skills do not end their
     * novicehood. Named here rather than in [MasterSkillTreeService] because two unrelated rules now read
     * it - the tree unlock and the gear gate - and the tree names are this registry's vocabulary.
     */
    const val NOVICE_TREE = "NOVICE"
  }
}

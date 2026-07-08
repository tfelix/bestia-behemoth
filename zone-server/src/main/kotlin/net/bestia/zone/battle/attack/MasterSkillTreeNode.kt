package net.bestia.zone.battle.attack

/**
 * Static, in-memory definition of a single skill in the bestia master's skill tree: which skill
 * it is, how many points can be invested into it, and which other nodes must be invested first
 * (see [MasterSkillPrerequisite]). Loaded from `master_skill_tree.yml` at boot by
 * [net.bestia.zone.boot.MasterSkillTreeImporterBootRunner] into [MasterSkillTreeRegistry] — this
 * is config, not player state, so it is never persisted to the database.
 */
data class MasterSkillTreeNode(
  val skillId: Long,
  val maxLevel: Int,
  val prerequisites: List<MasterSkillPrerequisite> = emptyList()
)

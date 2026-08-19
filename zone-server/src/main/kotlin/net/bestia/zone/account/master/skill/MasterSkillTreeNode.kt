package net.bestia.zone.account.master.skill

/**
 * Static, in-memory definition of a single skill in the bestia master's skill tree: which skill
 * it is, how many points can be invested into it, and which other nodes must be invested first
 * (see [MasterSkillPrerequisite]). Loaded from `master_skill_tree.yml` at boot by
 * [net.bestia.zone.boot.MasterSkillTreeImporterBootRunner] into [MasterSkillTreeRegistry] — this
 * is config, not player state, so it is never persisted to the database.
 *
 * [tree] and [subTree] used to be client-presentation-only (the grouping the Skills window reads
 * from its own Attack DB) but are now also load-bearing server-side: [MasterSkillTreeService]
 * gates investment outside the Novice tree behind Basic Skill 5, and gates a [subTree] behind
 * 5+ points spent anywhere in [tree].
 */
data class MasterSkillTreeNode(
  val skillId: Long,
  val maxLevel: Int,
  val tree: String,
  val subTree: String? = null,
  val prerequisites: List<MasterSkillPrerequisite> = emptyList()
)

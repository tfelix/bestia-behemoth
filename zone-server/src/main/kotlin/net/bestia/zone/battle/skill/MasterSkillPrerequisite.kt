package net.bestia.zone.battle.skill

/**
 * A single edge of the master skill tree's prerequisite DAG: the owning [MasterSkillTreeNode]
 * cannot receive any invested points until [prerequisiteSkillId] has been invested to at least
 * [requiredLevel]. A node may have zero, one, or several prerequisite edges.
 */
data class MasterSkillPrerequisite(
  val prerequisiteSkillId: Long,
  val requiredLevel: Int
)

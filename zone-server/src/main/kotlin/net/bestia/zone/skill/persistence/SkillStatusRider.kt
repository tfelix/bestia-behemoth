package net.bestia.zone.skill.persistence

/**
 * A status a skill's hit may leave behind, rolled against the target's resistance. [status] is a `StatusEffectId`
 * name; a [durationSeconds] or [level] of 0 leaves it to the effect's script and the skill's level.
 */
data class SkillStatusRider(
  val status: String = "",
  val chance: Int = 100,
  val durationSeconds: Double = 0.0,
  val level: Int = 0,
)

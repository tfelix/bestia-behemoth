package net.bestia.zone.ai.domain.bestia

/**
 * One attack a bestia can throw at a target, in whatever detail the planner needs to reason about it:
 * which [skillId] to actually cast, how close it has to be ([range]), how expensive it nominally is
 * ([baseCost]) before remembered [EffectivenessKey] effectiveness adjusts that cost up or down, and how
 * often it may be repeated ([cooldownSeconds]).
 *
 * [id] is the authoring name a profile and a log line use; [skillId] is the real row in the skill
 * catalogue that `SkillExecutionService` resolves. Keeping both means a profile stays readable while the
 * attack still goes through the same skill pipeline a player's attack does — range, mana, line of sight
 * and strategy script included. It defaults to the basic attack every mob is seeded with.
 */
data class AttackDefinition(
  val id: String,
  val range: Long,
  val baseCost: Float = 5f,
  val skillId: Long = BASIC_ATTACK_SKILL_ID,
  val cooldownSeconds: Float = 1.5f,
) {
  companion object {
    /** The skill every mob gets from the spawner, used when a profile names no explicit skill. */
    const val BASIC_ATTACK_SKILL_ID = 0L
  }
}

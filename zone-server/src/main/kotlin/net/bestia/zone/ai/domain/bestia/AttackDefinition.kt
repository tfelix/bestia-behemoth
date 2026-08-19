package net.bestia.zone.ai.domain.bestia

/**
 * One attack a bestia can throw at a target, in whatever detail the planner needs to reason about it:
 * how close it has to be ([range]), how expensive it nominally is ([baseCost]) before remembered
 * [EffectivenessKey] effectiveness adjusts that cost up or down, and how often it may be repeated
 * ([cooldownSeconds]).
 *
 * [id] is the authoring name a profile and a log line use. [skillId] is the catalogue row
 * `SkillExecutionService` resolves - **null means the basic attack**, which is not in the catalogue at all
 * and goes through `AttackExecutionService` instead. Null is the default because most mobs only bite.
 *
 * Keeping both means a profile stays readable while a real skill still goes through the same pipeline a
 * player's does - range, mana, line of sight and script included.
 */
data class AttackDefinition(
  val id: String,
  val range: Long,
  val baseCost: Float = 5f,
  val skillId: Long? = null,
  val cooldownSeconds: Float = 1.5f,
)

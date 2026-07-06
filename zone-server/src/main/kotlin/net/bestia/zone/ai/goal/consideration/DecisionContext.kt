package net.bestia.zone.ai.goal.consideration

import net.bestia.zone.ai.profile.AiProfile

/**
 * The distilled, lock-free view of an NPC's situation that both utility scoring and world-state
 * building read from. Built once per think tick from the `Brain` snapshot plus the NPC's own
 * components, so no stage downstream of perception needs to touch a foreign entity.
 */
data class DecisionContext(
  val profile: AiProfile,
  val ownHealthPct: Double,
  val enemyInSight: Boolean,
  val nearestEnemyDistance: Long?
)

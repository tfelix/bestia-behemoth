package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Walks into attack range of the current target. Only grounds when the bestia is further than
 * [BestiaDomain.ATTACK_RANGE] away, so an [AttackActionTemplate] naturally chains `approachTarget -> attack`,
 * while a target already in range skips this step entirely. The range is the reach of the species' default
 * attack, so a ranged creature stops at shooting distance.
 */
class ApproachTargetActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "approachTarget"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val targetPosition = state.get(BestiaDomain.TARGET_POSITION) ?: return emptyList()
    val attackRange = state.get(BestiaDomain.ATTACK_RANGE) ?: BestiaDomain.DEFAULT_ATTACK_RANGE
    if (position.distance(targetPosition) <= attackRange) return emptyList()

    return listOf(
      Action(
        name = "approachTarget",
        effects = listOf(Effects.set(BestiaDomain.POSITION, targetPosition)),
        cost = { position.distance(targetPosition).toFloat() },
        // The target's last known position, captured at grounding time. Perception refreshes it every
        // sweep, and a stale chase ends when the plan is reconsidered rather than being tracked here.
        behavior = { MoveTo(targetPosition, locomotion, arrivalRadius = attackRange) },
      )
    )
  }
}

package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Walks into melee range of the current target. Only grounds when the bestia is further than
 * [BestiaDomain.MELEE_RANGE] away, so a melee [AttackActionTemplate] naturally chains
 * `approachTarget -> attack`, while an already-in-range ranged attack skips this step entirely.
 *
 * Simplification: this walks all the way to melee distance rather than "just within the chosen attack's
 * range," which is fine for melee but means a ranged attacker currently closes more distance than it
 * strictly needs to before ever getting a chance to fire from range.
 */
class ApproachTargetActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "approachTarget"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val targetPosition = state.get(BestiaDomain.TARGET_POSITION) ?: return emptyList()
    val meleeRange = state.get(BestiaDomain.MELEE_RANGE) ?: BestiaDomain.DEFAULT_MELEE_RANGE
    if (position.distance(targetPosition) <= meleeRange) return emptyList()

    return listOf(
      Action(
        name = "approachTarget",
        effects = listOf(Effects.set(BestiaDomain.POSITION, targetPosition)),
        cost = { position.distance(targetPosition).toFloat() },
        // The target's last known position, captured at grounding time. Perception refreshes it every
        // sweep, and a stale chase ends when the plan is reconsidered rather than being tracked here.
        behavior = { MoveTo(targetPosition, locomotion, arrivalRadius = meleeRange) },
      )
    )
  }
}

package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Walks into melee range of the current target. Only grounds when the bestia is further than
 * [BestiaDomain.MELEE_RANGE] away, so a melee [AttackActionTemplate] naturally chains
 * `approachTarget -> attack`, while an already-in-range ranged attack skips this step entirely.
 *
 * Simplification: this walks all the way to melee distance rather than "just within the chosen
 * attack's range," which is fine for melee but means a ranged attacker currently closes more
 * distance than it strictly needs to before ever getting a chance to fire from range.
 */
class ApproachTargetActionTemplate : ActionTemplate {
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
      )
    )
  }
}

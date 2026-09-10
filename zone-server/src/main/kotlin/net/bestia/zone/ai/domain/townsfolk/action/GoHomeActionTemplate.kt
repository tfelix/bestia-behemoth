package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Walks to the doorstep.
 *
 * Grounds only from away, so it cannot be chained onto itself, and it walks all the way to
 * [TownsfolkDomain.DOORSTEP_RADIUS] rather than to the loiter radius the `GoHome` *goal* is happy with -
 * because the other reader is [SleepAtHomeActionTemplate], whose precondition is the doorstep. Stopping at
 * the edge of the loiter radius would leave the planner with a `goHome` that does not enable the `sleep`
 * it was chosen to enable.
 */
class GoHomeActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goHome"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(TownsfolkDomain.HOME_POSITION) ?: return emptyList()
    // A journey cannot be planned without knowing where it starts, or the search would use this action's
    // positional effect to invent a position that was never observed. See `ReturnHomeActionTemplate`.
    val position = state.get(TownsfolkDomain.POSITION) ?: return emptyList()
    if (position.distance(home) <= TownsfolkDomain.DOORSTEP_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "goHome",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, home)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(home) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(home, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}

package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/** Walks to the counter. [GoHomeActionTemplate]'s twin, and grounds only from away for the same reason. */
class GoToMealActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goToMeal"

  override fun ground(state: WorldState): List<Action> {
    val stall = state.get(TownsfolkDomain.MEAL_POSITION) ?: return emptyList()
    val position = state.get(TownsfolkDomain.POSITION) ?: return emptyList()
    if (position.distance(stall) <= TownsfolkDomain.DOORSTEP_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "goToMeal",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, stall)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(stall) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(stall, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}

package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Preconditions
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Eats what was bought, wherever they happen to be standing.
 *
 * The precondition is what makes the planner chain `goToMeal -> buyFood -> eat` from anywhere in town,
 * which is the longest plan the domain has and the best thing in it to watch.
 */
class EatActionTemplate : ActionTemplate {
  override val id = "eat"

  override fun ground(state: WorldState): List<Action> {
    return listOf(
      Action(
        name = "eat",
        preconditions = listOf(Preconditions.equalTo(TownsfolkDomain.HAS_FOOD, true)),
        effects = listOf(
          Effects.set(TownsfolkDomain.HUNGER, TownsfolkDomain.FED_HUNGER),
          Effects.set(TownsfolkDomain.HAS_FOOD, false),
        ),
        cost = { 1f },
        behavior = { Wait(EATING_SECONDS) },
      )
    )
  }

  private companion object {
    const val EATING_SECONDS = 3f
  }
}

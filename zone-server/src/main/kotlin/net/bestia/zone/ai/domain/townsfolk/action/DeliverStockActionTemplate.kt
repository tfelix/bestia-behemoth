package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Puts the goods on the shelf, which is what ends the errand.
 *
 * Needs both halves - standing at the counter *and* carrying something - and that pair is what makes the
 * planner walk the whole errand rather than skipping to the end: with an empty basket there is no way to
 * reach this, so the search has to find the trip to the maker first.
 */
class DeliverStockActionTemplate : ActionTemplate {
  override val id = "deliverStock"

  override fun ground(state: WorldState): List<Action> {
    val today = state.get(TownsfolkDomain.DAY_INDEX) ?: return emptyList()

    return listOf(
      Action(
        name = "deliverStock",
        preconditions = listOf(
          Precondition { TownsfolkDomain.isAtWork(it) },
          Precondition { it.get(TownsfolkDomain.CARRYING_STOCK) == true },
        ),
        effects = listOf(
          Effects.set(TownsfolkDomain.RESTOCKED_ON_DAY, today),
          Effects.set(TownsfolkDomain.CARRYING_STOCK, false),
        ),
        cost = { 2f },
        behavior = { Wait(UNLOADING_SECONDS) },
      )
    )
  }

  private companion object {
    const val UNLOADING_SECONDS = 3f
  }
}

package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Takes delivery at the maker's door.
 *
 * No coin and no ledger entry, for the reason [BuyFoodActionTemplate] carries: a shop's stock is already
 * inside the throughput the settlement is derived from, so this is the errand being *rendered*, not a
 * second way for goods to move. What the maker's shelves decide is whether the errand happens at all.
 */
class CollectStockActionTemplate : ActionTemplate {
  override val id = "collectStock"

  override fun ground(state: WorldState): List<Action> {
    if (state.get(TownsfolkDomain.SUPPLY_IN_STOCK) != true) return emptyList()
    state.get(TownsfolkDomain.SUPPLIER_POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "collectStock",
        preconditions = listOf(Precondition { TownsfolkDomain.isAtSupplier(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.CARRYING_STOCK, true)),
        cost = { 2f },
        behavior = { Wait(LOADING_SECONDS) },
      )
    )
  }

  private companion object {
    /** Long enough to read as loading a basket from across the square. */
    const val LOADING_SECONDS = 3f
  }
}

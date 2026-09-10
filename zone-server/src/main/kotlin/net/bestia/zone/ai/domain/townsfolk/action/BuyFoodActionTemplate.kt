package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Buys a meal at the counter.
 *
 * No coin and no ledger entry. Household consumption is already inside the reference throughput every
 * settlement is derived from, so a villager buying a loaf is *rendering* a meal the books have already
 * accounted for - the same rule that stops the visible baker producing. What the town's larder decides
 * is whether the meal exists at all, which is how a burnt field reaches the square.
 *
 * Grounds only where there is something to buy, which is what makes going hungry visible rather than a
 * townsperson standing at a counter forever.
 */
class BuyFoodActionTemplate : ActionTemplate {
  override val id = "buyFood"

  override fun ground(state: WorldState): List<Action> {
    if (state.get(TownsfolkDomain.MEAL_IN_STOCK) != true) return emptyList()
    state.get(TownsfolkDomain.MEAL_POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "buyFood",
        preconditions = listOf(Precondition { TownsfolkDomain.isAtMeal(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.HAS_FOOD, true)),
        cost = { 2f },
        behavior = { Wait(BUYING_SECONDS) },
      )
    )
  }

  private companion object {
    /** Long enough to read as a transaction from across the square, short enough not to hold up a queue. */
    const val BUYING_SECONDS = 2f
  }
}

package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import org.springframework.stereotype.Component

/**
 * Notices what a townsperson's settlement offers them: a meal, stock to fetch, work to do and somewhere
 * to spend the evening.
 *
 * A [Sense] rather than a system, so it adds no scheduler wave and costs a `has` for everybody it does
 * not concern - which is every creature in the world. `ShelterSense`'s shape.
 *
 * ### The visible villager eats nothing
 *
 * Nothing here or downstream of it moves the ledger. Household consumption is already in the reference
 * throughput every settlement is derived from, so a villager who walks to a stall and eats is
 * *rendering* a meal the books have already accounted for - the same rule that stops a visible baker
 * producing. Deducting it again would have a watched town starve where an unwatched one did not.
 *
 * What the town's larder does decide is whether the meal is *available*, which is how burning the field
 * outside a village reaches the people in its square.
 */
@Component
class SettlementSense(
  private val food: SettlementFood,
  private val work: SettlementWork,
  private val gathering: SettlementGathering,
) : Sense {

  override val name = "settlement"

  /**
   * Slow, because none of it changes quickly: where the market is never moves, and how much bread the
   * town has moves on a game-day. This is the cheap end of the sense budget and it should stay there.
   */
  override val intervalSeconds = 8f

  override val reads: ComponentClassSet = setOf(Townsfolk::class)

  override fun sense(context: SenseContext) {
    val townsfolk = context.world.get(context.entityId, Townsfolk::class) ?: return

    senseMeal(context)
    senseWork(context, TownsfolkIdentity.settlementOf(townsfolk.identity))
    senseSupplier(context)
    senseGathering(context)
  }

  private fun senseGathering(context: SenseContext) {
    val spot = gathering.spotNear(context.position)
    if (spot == null) {
      context.forget(TownsfolkDomain.SOCIAL_POSITION)
      return
    }

    context.remember(TownsfolkDomain.SOCIAL_POSITION, spot)
  }

  private fun senseMeal(context: SenseContext) {
    val stall = food.stallNear(context.position)
    if (stall == null) {
      context.forget(TownsfolkDomain.MEAL_POSITION)
      context.forget(TownsfolkDomain.MEAL_IN_STOCK)
      return
    }

    context.remember(TownsfolkDomain.MEAL_POSITION, stall.doorstep)
    context.remember(TownsfolkDomain.MEAL_IN_STOCK, stall.inStock)
  }

  /**
   * Only ever written for somebody who has a recipe. Everyone else has to be left *absent* rather than
   * told they are supplied, because absence is what the goal reads as "nothing is stopping me" - see
   * [TownsfolkDomain.hasSomethingToWorkWith].
   */
  private fun senseSupplier(context: SenseContext) {
    val supply = work.supplierNear(
      context.position,
      context.recall(TownsfolkDomain.OCCUPATION)?.businessType,
    )
    if (supply == null) {
      context.forget(TownsfolkDomain.SUPPLIER_POSITION)
      context.forget(TownsfolkDomain.SUPPLY_IN_STOCK)
      return
    }

    context.remember(TownsfolkDomain.SUPPLIER_POSITION, supply.doorstep)
    context.remember(TownsfolkDomain.SUPPLY_IN_STOCK, supply.inStock)
  }

  private fun senseWork(context: SenseContext, settlement: Int) {
    val trade = work.tradeOf(context.recall(TownsfolkDomain.OCCUPATION)?.businessType) ?: return

    context.remember(TownsfolkDomain.WORK_SUPPLIED, work.canSupply(settlement, trade))
  }
}

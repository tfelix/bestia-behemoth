package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import org.springframework.stereotype.Component

/**
 * Notices where a townsperson can buy a meal, and whether the town has one to sell.
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
) : Sense {

  override val name = "settlement"

  /**
   * Slow, because none of it changes quickly: where the market is never moves, and how much bread the
   * town has moves on a game-day. This is the cheap end of the sense budget and it should stay there.
   */
  override val intervalSeconds = 8f

  override val reads: ComponentClassSet = setOf(Townsfolk::class)

  override fun sense(context: SenseContext) {
    if (!context.world.has(context.entityId, Townsfolk::class)) return

    val stall = food.stallNear(context.position)
    if (stall == null) {
      context.forget(TownsfolkDomain.MEAL_POSITION)
      context.forget(TownsfolkDomain.MEAL_IN_STOCK)
      return
    }

    context.remember(TownsfolkDomain.MEAL_POSITION, stall.doorstep)
    context.remember(TownsfolkDomain.MEAL_IN_STOCK, stall.inStock)
  }
}

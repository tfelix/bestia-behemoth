package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.item.care.BagCare
import net.bestia.zone.item.ecs.Inventory
import org.springframework.stereotype.Component

/**
 * Notices how much stamina a creature has left and whether its own bag could help, so an owned bestia eats or
 * puts on a bandage before it is too late. Wild mobs carry no bag and never learn the bag keys.
 */
@Component
class BagSense(
  private val care: BagCare,
) : Sense {

  override val name = "bag"

  override val intervalSeconds = 1f

  override val reads: ComponentClassSet = setOf(Stamina::class) + BagCare.READS

  override fun sense(context: SenseContext) {
    context.world.get(context.entityId, Stamina::class)?.let { stamina ->
      context.remember(BestiaDomain.STAMINA_PCT, percentOf(stamina.current, stamina.max))
    }

    val inventory = context.world.get(context.entityId, Inventory::class)
    if (inventory == null) {
      context.forget(BestiaDomain.FOOD_IN_BAG)
      context.forget(BestiaDomain.HEAL_IN_BAG)
      return
    }

    context.remember(BestiaDomain.FOOD_IN_BAG, care.hasFor(inventory, BagCare.Need.STAMINA))
    context.remember(BestiaDomain.HEAL_IN_BAG, care.hasFor(inventory, BagCare.Need.HEALTH))
  }

  private fun percentOf(current: Int, max: Int): Int = if (max <= 0) 100 else current * 100 / max
}

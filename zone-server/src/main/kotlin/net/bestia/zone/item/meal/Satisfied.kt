package net.bestia.zone.item.meal

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 311 (`SATISFIED`, "Gesättigt"): a meal's buff. The effect carries the item id of the meal
 * that was eaten as its [ActiveStatusEffect.amount], and grants that meal's `bonus` from `meals.yml`.
 *
 * Only ever one: `MealScript` takes the old one off before laying the new one on, so eating again swaps the whole
 * bonus set and the timer for the new meal's.
 */
@Component
class Satisfied(
  private val meals: MealCatalog,
) : StatusEffectScript {

  /** A meal always says how long; this is only what a stray application without one gets. */
  override fun durationSeconds(level: Int): Double = FALLBACK_SECONDS

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    meals.of(effect.amount.toLong())?.bonus?.applyTo(context)
  }

  private companion object {
    const val FALLBACK_SECONDS = 900.0
  }
}

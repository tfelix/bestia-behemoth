package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `SPRINTING`: Sprint's burst, +30% speed at rank 1 and +10% per rank above, gone after an attack or a cast. */
@Component
class Sprinting : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.speed *= 1f + speedPercent(effect.level) / 100f
  }

  override fun onActed(context: StatusEffectHookContext, action: EntityAction) {
    if (action == EntityAction.ATTACKED || action == EntityAction.CAST) {
      context.removeSelf()
    }
  }

  private fun speedPercent(level: Int): Int = BASE_PERCENT + PERCENT_PER_RANK * (level.coerceIn(1, MAX_RANK) - 1)

  private companion object {
    const val DURATION_SECONDS = 5.0
    const val BASE_PERCENT = 30
    const val PERCENT_PER_RANK = 10
    const val MAX_RANK = 3
  }
}

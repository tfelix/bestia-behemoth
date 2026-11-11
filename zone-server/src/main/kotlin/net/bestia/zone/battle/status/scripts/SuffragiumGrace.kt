package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 213 (`SUFFRAGIUM`): the bearer's next cast bar within 30 s is 30% shorter. The bar that
 * uses it spends it, see `ActivateSkillHandler`.
 */
@Component
class SuffragiumGrace : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.addCastTimeReduction(CAST_TIME_REDUCTION_PERCENT)
  }

  private companion object {
    const val DURATION_SECONDS = 30.0
    const val CAST_TIME_REDUCTION_PERCENT = 30
  }
}

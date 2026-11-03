package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Limbs going stiff under a petrifying gaze: slower steps and slower swings. */
@Component
class Stiffened : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 6.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.speed *= SPEED_FACTOR
    context.addPercent(aspd = ASPD_PERCENT)
  }

  private companion object {
    const val SPEED_FACTOR = 0.7f
    const val ASPD_PERCENT = -30
  }
}

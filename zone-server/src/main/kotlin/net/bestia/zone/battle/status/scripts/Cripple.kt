package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Registered under `status_effects.yml` id 2 (`CRIPPLE`) - a flat -15% speed debuff. */
@Component
class Cripple : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = 15.0

  override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
    context.speed *= 0.85f
  }
}

package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Registered under `status_effects.yml` id 1 (`SWIFTNESS`) - a flat +10%/level speed buff. */
@Component
class Swiftness : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = 30.0 + 5.0 * (level - 1)

  override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
    context.speed *= 1.0f + 0.1f * level
  }
}

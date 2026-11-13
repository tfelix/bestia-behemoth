package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 232 (`DISSONANCE`): the Bard's Dissonant Chord, `-5%/lv` ASPD and `-3%/lv` HIT. */
@Component
class Dissonance : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    val level = effect.level.coerceIn(1, MAX_LEVEL)

    context.addPercent(aspd = -ASPD_PERCENT_PER_LEVEL * level, hit = -HIT_PERCENT_PER_LEVEL * level)
  }

  private companion object {
    const val DURATION_SECONDS = 20.0
    const val ASPD_PERCENT_PER_LEVEL = 5
    const val HIT_PERCENT_PER_LEVEL = 3
    const val MAX_LEVEL = 5
  }
}

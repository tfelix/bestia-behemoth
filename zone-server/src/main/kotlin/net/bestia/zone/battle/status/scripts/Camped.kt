package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `CAMPED`: standing in a Base Camp. The camp keeps refreshing it, so it falls off a few seconds after walking out.
 * Its temperature help and, from Lv.2, its storm shelter are read by the exposure system; Lv.2 also speeds HP and
 * stamina recovery here.
 */
@Component
class Camped : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false
  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return LINGER_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    if (effect.level >= SHELTER_LEVEL) {
      context.addHpRegen(percent = REGEN_PERCENT)
      context.addStaminaRegen(percent = REGEN_PERCENT)
    }
  }

  companion object {
    const val LINGER_SECONDS = 5.0
    const val SHELTER_LEVEL = 2
    const val REGEN_PERCENT = 50
    const val TOLERANCE_CELSIUS = 5.0
  }
}

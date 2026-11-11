package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 211 (`INCREASE_AGI`): the Priest's Increase AGI. `+15%` speed at every level, on top of
 * Quick Travel, with `+2/lv` AGI from +4 and `+2%/lv` attack speed from 1%, for `40 s + 40 s/lv`.
 */
@Component
class AgilityIncreased : StatusEffectScript {

  override fun durationSeconds(level: Int): Double = SECONDS_PER_LEVEL * (level + 1)

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    val level = effect.level.coerceIn(1, MAX_LEVEL)

    context.speed *= 1f + SPEED_PERCENT / 100f
    context.agility += AGI_PER_LEVEL * (level + 1)
    context.addPercent(aspd = ASPD_PERCENT_PER_LEVEL * level - 1)
  }

  private companion object {
    const val SECONDS_PER_LEVEL = 40.0
    const val SPEED_PERCENT = 15
    const val AGI_PER_LEVEL = 2
    const val ASPD_PERCENT_PER_LEVEL = 2
    const val MAX_LEVEL = 3
  }
}

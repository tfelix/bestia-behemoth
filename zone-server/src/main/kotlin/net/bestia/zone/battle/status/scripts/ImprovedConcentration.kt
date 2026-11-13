package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 231 (`IMPROVE_CONCENTRATION`): the Hunter's Improve Concentration, `+2/lv` AGI and DEX. */
@Component
class ImprovedConcentration : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    val level = effect.level.coerceIn(1, MAX_LEVEL)

    context.agility += STAT_PER_LEVEL * level
    context.dexterity += STAT_PER_LEVEL * level
  }

  private companion object {
    const val DURATION_SECONDS = 60.0
    const val STAT_PER_LEVEL = 2
    const val MAX_LEVEL = 5
  }
}

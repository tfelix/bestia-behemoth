package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Aged by a curse: a weaker body that heals slowly. The smaller pool only reaches a body whose pool follows its
 * attributes, a master or an owned bestia; a wild species keeps its authored health.
 */
@Component
class Withered : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 30.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.vitality = context.vitality * VITALITY_PERCENT / 100
    context.addMaxPools(hpPercent = MAX_HP_PERCENT)
    context.addHpRegen(percent = HP_REGEN_PERCENT)
  }

  private companion object {
    const val VITALITY_PERCENT = 80
    const val MAX_HP_PERCENT = -20
    const val HP_REGEN_PERCENT = -50
  }
}

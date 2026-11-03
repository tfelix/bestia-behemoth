package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 18 (`WEAKENED`), from a bestia's Growl: the target deals a fifth less
 * damage.
 */
@Component
class Weakened : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = ATTACK_PERCENT)
  }

  private companion object {
    const val DURATION_SECONDS = 10.0
    const val ATTACK_PERCENT = -20
  }
}

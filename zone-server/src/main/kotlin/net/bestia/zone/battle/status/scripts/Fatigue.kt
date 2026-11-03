package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** What an [Enraged] fit leaves behind: weaker in every way for a few seconds, and too spent to rage again. */
@Component
class Fatigue : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 8.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = -20, defense = -20, aspd = -20)
  }
}

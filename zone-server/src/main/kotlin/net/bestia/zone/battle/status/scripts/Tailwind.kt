package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The wind at its back: quicker swings and quicker steps. */
@Component
class Tailwind : StatusEffectScript {

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
    context.addPercent(aspd = 20)
    context.speed *= 1.25f
  }
}

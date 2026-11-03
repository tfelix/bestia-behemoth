package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** All attack and no guard: armour counts for nothing while it lasts. */
@Component
class Berserk : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 15.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = 60, defense = -100, aspd = 30)
  }
}

package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** The price of an all-out blow: no natural mana regeneration for five minutes. */
@Component
class ManaSealed : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 300.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.blockRegen(mana = true)
  }
}

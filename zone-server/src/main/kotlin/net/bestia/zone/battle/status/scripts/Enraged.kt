package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Hits harder and shrugs blows off, then pays with [Fatigue] however the rage ends. */
@Component
class Enraged : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 15.0
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    context.applyEffect(context.hostId, StatusEffectId.FATIGUE, context.effect.level)
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = 25, defense = 25)
  }
}

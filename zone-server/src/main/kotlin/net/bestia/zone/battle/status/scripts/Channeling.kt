package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Holds a channelling caster still and its weapon down. Moving is not locked: a move request is how a player
 * breaks off the channel, see `CastCancelService.cancelCast`.
 */
@Component
class Channeling : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = setOf(ActionLock.ATTACK)
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.speed = 0f
  }
}

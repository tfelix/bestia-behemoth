package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Held in place: the bearer still fights and casts, it only cannot move. */
@Component
class Immobilized : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = setOf(ActionLock.MOVE)

  override fun durationSeconds(level: Int): Double {
    return 3.0
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

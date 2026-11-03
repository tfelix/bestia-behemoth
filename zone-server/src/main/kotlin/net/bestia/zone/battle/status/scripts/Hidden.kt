package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Dug in out of sight (Ragnarok Online's Hiding): no moving, but area attacks pass over it, see `AreaEffectSystem`. */
@Component
class Hidden(visibility: EntityVisibility) : Concealment(visibility) {

  override val actionLocks: Set<ActionLock> = setOf(ActionLock.MOVE)

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

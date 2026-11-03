package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Knocked senseless: no moving, swinging, casting or drinking, and whatever was being cast is lost. */
@Component
class Stun(private val castCancelService: CastCancelService) : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = ActionLock.ALL

  override fun durationSeconds(level: Int): Double {
    return 2.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    castCancelService.interrupt(context.world, context.hostId)
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

package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Turned to stone: locked, weighed as Earth 1 and very hard to hurt. Unlike ice, a blow does not break it. */
@Component
class Stone(private val castCancelService: CastCancelService) : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = ActionLock.ALL

  override fun durationSeconds(level: Int): Double {
    return 30.0
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
    context.elementOverride = Element.EARTH
    context.addPercent(defense = DEFENSE_PERCENT)
  }

  private companion object {
    const val DEFENSE_PERCENT = 90
  }
}

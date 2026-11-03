package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Moving unseen (Ragnarok Online's Cloaking): slower, and any blow, an area one included, gives it away. */
@Component
class Cloaked(visibility: EntityVisibility) : Concealment(visibility) {

  override fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
    if (amount > 0) {
      context.removeSelf()
    }

    return amount
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.speed *= SPEED_FACTOR
  }

  private companion object {
    const val SPEED_FACTOR = 0.7f
  }
}

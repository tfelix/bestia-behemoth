package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Stuck in a quagmire: clumsy, slow, and stripped of any haste. Its zone refreshes it every tick, so it ends soon
 * after the bearer wades out.
 */
@Component
class Quagmired : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 2.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    context.removeEffect(StatusEffectId.SWIFTNESS)
    context.removeEffect(StatusEffectId.TAILWIND)
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.dexterity /= 2
    context.agility /= 2
    context.speed *= SPEED_FACTOR
  }

  private companion object {
    const val SPEED_FACTOR = 0.7f
  }
}

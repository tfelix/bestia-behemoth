package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Encased in ice: locked like a stun, weighed as Water 1 and a little harder to hurt by blows and spells alike,
 * until the next blow shatters it. Fire creatures cannot be frozen at all.
 */
@Component
class Frozen(private val castCancelService: CastCancelService) : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = ActionLock.ALL

  override fun durationSeconds(level: Int): Double {
    return 4.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    if (context.world.get(context.hostId, Nature::class)?.element?.base == Element.FIRE) {
      context.removeSelf()
      return
    }

    castCancelService.interrupt(context.world, context.hostId)
  }

  override fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
    // The blow that froze the body lands in the same moment, and must not shatter the ice it made.
    if (amount > 0 && context.effect.ageSeconds > 0f) {
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
    context.speed = 0f
    context.elementOverride = Element.WATER
    context.addPercent(defense = DEFENSE_PERCENT)
  }

  private companion object {
    const val DEFENSE_PERCENT = 10
  }
}

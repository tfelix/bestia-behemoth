package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Gear a strip skill tore off. Its slot stays locked while this lasts, see `SlotLock`. On a body with no gear to take,
 * the effect's amount weakens it instead: a disarm cuts its ATK by that percent, an armor strip its DEF.
 */
@Component
class StrippedGear : StatusEffectScript {

  /** Strip Equipment's table: 5 s a level. */
  override fun durationSeconds(level: Int): Double {
    return 5.0 * level
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    if (effect.amount <= 0) return

    when (effect.definitionId) {
      StatusEffectId.DISARMED.id -> context.addPercent(attack = -effect.amount)
      StatusEffectId.ARMOR_STRIPPED.id -> context.cutDefense(effect.amount)
    }
  }
}

package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 218 (`MINDBREAK`): for 60 s the bestia's MATK rises by the instance's `amount` in percent,
 * and its soft MDEF falls by `20%` at Lv.1 and `40%` more per level.
 *
 * Not persisted: `amount` carries the caster's Arcane Mastery, which a stored copy would lose.
 */
@Component
class Mindbroken : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.addMagicAttackPercent(effect.amount)
    context.cutMagicDefense(percentOfLevel(effect.level))
  }

  companion object {
    /** Both halves of the table, MATK gained and soft MDEF lost: 20/60/100%. */
    fun percentOfLevel(level: Int): Int {
      return FIRST_LEVEL_PERCENT + PERCENT_PER_LEVEL * (level.coerceIn(1, MAX_LEVEL) - 1)
    }

    private const val DURATION_SECONDS = 60.0
    private const val FIRST_LEVEL_PERCENT = 20
    private const val PERCENT_PER_LEVEL = 40
    private const val MAX_LEVEL = 3
  }
}

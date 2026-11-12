package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 228 (`JUGGERNAUT`): +20% ATK and DEF for 10 s, 15 s from Lv.2. From Lv.2 the Knight cannot
 * be stunned or knocked back; at Lv.3 a blow its shield catches whole starts the clock again.
 */
@Component
class JuggernautStance : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return if (level >= UNSTOPPABLE_FROM_LEVEL) 15.0 else 10.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = ATK_AND_DEF_PERCENT)
    // Hard DEF counts percent of damage removed, so 20 points is the skill's +20% DEF.
    context.addDefense(hardDefense = ATK_AND_DEF_PERCENT)
    if (level >= UNSTOPPABLE_FROM_LEVEL) {
      context.addStatusImmunities(StatusEffectId.STUN)
      context.knockbackImmune = true
    }
  }

  override fun onBlocked(context: StatusEffectHookContext) {
    val level = context.effect.level
    if (level >= RENEWED_BY_BLOCK_FROM_LEVEL) {
      context.refreshSelf(durationSeconds(level))
    }
  }

  private companion object {
    const val ATK_AND_DEF_PERCENT = 20
    const val UNSTOPPABLE_FROM_LEVEL = 2
    const val RENEWED_BY_BLOCK_FROM_LEVEL = 3
  }
}

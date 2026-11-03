package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Turns single-target skill damage back on its caster, up to one hit per level, see `BudgetedSkillWorld.apply`.
 * The count of hits left lives in [net.bestia.zone.battle.ecs.effects.ActiveStatusEffect.count].
 */
@Component
class Reflect : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 30.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    context.effect.count = context.effect.level
  }
}

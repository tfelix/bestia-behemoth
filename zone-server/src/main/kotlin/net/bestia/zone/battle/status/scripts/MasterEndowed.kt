package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 205 (`MASTER_ENDOWED`): the Sage's Endow. The weapon strikes as the element stored in
 * [ActiveStatusEffect.amount], with `+5%/lv` attack, for `60 s/lv`.
 */
@Component
class MasterEndowed : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = SECONDS_PER_LEVEL * level

  override fun onApply(context: StatusEffectHookContext) {
    context.removeEffect(StatusEffectId.ASPERSIO)
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.weaponElement = Element.entries[effect.amount]
    context.addPercent(attack = ATTACK_PERCENT_PER_LEVEL * effect.level)
  }

  private companion object {
    const val SECONDS_PER_LEVEL = 60.0
    const val ATTACK_PERCENT_PER_LEVEL = 5
  }
}

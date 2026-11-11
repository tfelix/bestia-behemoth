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
 * `status_effects.yml` id 216 (`ASPERSIO`): the Priest's Aspersio. The weapon strikes as holy for `60 s/lv`. A
 * weapon holds one element, so it replaces an endowment, and an endowment replaces it.
 */
@Component
class AspersioAnointing : StatusEffectScript {

  override val isPersisted: Boolean = false

  override val element: Element = Element.HOLY

  override fun durationSeconds(level: Int): Double {
    return SECONDS_PER_LEVEL * level
  }

  override fun onApply(context: StatusEffectHookContext) {
    context.removeEffect(StatusEffectId.MASTER_ENDOWED)
    context.removeEffect(StatusEffectId.ENDOWED)
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.weaponElement = Element.HOLY
  }

  private companion object {
    const val SECONDS_PER_LEVEL = 60.0
  }
}

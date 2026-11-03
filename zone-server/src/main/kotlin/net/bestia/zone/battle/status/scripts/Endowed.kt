package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A weapon endowed with an element: plain melee blows hit as the element the skill stored in
 * [ActiveStatusEffect.amount]. Not kept over a relog, because that store is runtime only.
 */
@Component
class Endowed : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 180.0
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.weaponElement = Element.entries[effect.amount]
  }
}

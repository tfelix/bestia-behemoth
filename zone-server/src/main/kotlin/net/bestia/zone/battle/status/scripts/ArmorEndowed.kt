package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A coat of an element: hits on the bearer are weighed against the element stored like [Endowed]'s. */
@Component
class ArmorEndowed : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 180.0
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    context.elementOverride = Element.entries[effect.amount]
  }
}

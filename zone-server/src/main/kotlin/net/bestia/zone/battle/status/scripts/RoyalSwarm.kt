package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `ROYAL_SWARM`: near a master who wears the Hive Queen's essence. Kept up by the essence aura's pulse. */
@Component
class RoyalSwarm : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return EssenceAuraLinger.SECONDS
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
    context.addPercent(aspd = 10)
  }
}

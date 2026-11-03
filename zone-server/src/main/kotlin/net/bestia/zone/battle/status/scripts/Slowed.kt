package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Ten percent slower per level, never to a halt: holding something in place is [Immobilized]. */
@Component
class Slowed : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 3.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.speed *= (1f - PER_LEVEL * level).coerceAtLeast(MIN_FACTOR)
  }

  private companion object {
    const val PER_LEVEL = 0.1f
    const val MIN_FACTOR = 0.1f
  }
}

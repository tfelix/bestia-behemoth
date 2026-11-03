package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Hits less often. A creature also sees only what stands next to it, see `PerceptionSystem`. */
@Component
class Blind : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(hit = HIT_PERCENT)
  }

  companion object {
    const val HIT_PERCENT = -25

    /** How far a blinded creature still notices a hostile, in tiles. */
    const val SIGHT_RADIUS = 2
  }
}

package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 15 (`EMERGENCY_MOVE`), from [EmergencyMove]. */
@Component
class EmergencyMoveEffect : StatusEffectScript {

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
    context.speed *= SPEED_FACTOR
  }

  private companion object {
    const val SPEED_FACTOR = 1.25f
  }
}

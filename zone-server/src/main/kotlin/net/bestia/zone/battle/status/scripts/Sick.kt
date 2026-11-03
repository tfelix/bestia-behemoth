package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Every heal from a skill, an item or an effect restores only half, see `Healing`. */
@Component
class Sick : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 20.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(healReceived = HEAL_RECEIVED_PERCENT)
  }

  private companion object {
    const val HEAL_RECEIVED_PERCENT = -50
  }
}

package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 18 (`WEAKENED`), the debuff archetype of bestia skills: the target
 * deals about a fifth less damage. Lowered through the attributes, like [Empowered].
 */
@Component
class Weakened : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.strength = (context.strength * DAMAGE_FACTOR).toInt()
    context.dexterity = (context.dexterity * DAMAGE_FACTOR).toInt()
    context.intelligence = (context.intelligence * DAMAGE_FACTOR).toInt()
  }

  private companion object {
    const val DURATION_SECONDS = 10.0
    const val DAMAGE_FACTOR = 0.8f
  }
}

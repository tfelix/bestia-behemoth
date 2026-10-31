package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 17 (`EMPOWERED`), the buff archetype of bestia skills: the bestia
 * deals about a quarter more damage. Raised through the attributes ATK and MATK are built from, because
 * damage has no multiplier a status effect can reach yet.
 */
@Component
class Empowered : StatusEffectScript {

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
    const val DURATION_SECONDS = 15.0
    const val DAMAGE_FACTOR = 1.25f
  }
}

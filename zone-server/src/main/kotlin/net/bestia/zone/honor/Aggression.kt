package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Whether harming an entity is defense, because the master who answers for it started a fight. */
@Component
class Aggression(
  private val masters: MasterEntityResolver,
) {

  fun isAggressor(world: World, entity: EntityId): Boolean {
    return aggressionOf(world, entity) != null
  }

  /** The entity the master behind [entity] first attacked, or null when they are no aggressor. */
  fun victimOf(world: World, entity: EntityId): EntityId? {
    return aggressionOf(world, entity)?.sourceEntityId
  }

  private fun aggressionOf(world: World, entity: EntityId): ActiveStatusEffect? {
    val master = masters.masterEntityOf(world, entity) ?: return null

    return world.get(master, StatusEffects::class)?.activeEffects?.firstOrNull {
      it.definitionId == StatusEffectId.AGGRESSOR.id
    }
  }
}

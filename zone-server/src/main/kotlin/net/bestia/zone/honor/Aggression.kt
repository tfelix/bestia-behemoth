package net.bestia.zone.honor

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
    val master = masters.masterEntityOf(world, entity) ?: return false

    return world.get(master, StatusEffects::class)?.hasEffect(StatusEffectId.AGGRESSOR.id) == true
  }
}

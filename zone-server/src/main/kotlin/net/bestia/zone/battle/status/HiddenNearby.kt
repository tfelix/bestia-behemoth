package net.bestia.zone.battle.status

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId

/** What a reveal finds: everything under one of the [StatusEffectId.CONCEALMENTS] near a spot. */
object HiddenNearby {

  fun around(world: World, centre: Vec3L, radiusTiles: Long, except: EntityId): List<EntityId> {
    val hidden = ArrayList<EntityId>()
    world.query(StatusEffects::class, Position::class).each { id ->
      if (id != except && get<StatusEffects>().isConcealed() && get<Position>().toVec3L().distance(centre) <= radiusTiles) {
        hidden += id
      }
    }

    return hidden
  }
}

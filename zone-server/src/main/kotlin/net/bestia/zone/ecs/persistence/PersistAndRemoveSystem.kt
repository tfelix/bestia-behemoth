package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.item.GroundItemStack
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.persistence.persisters.MasterEntityPersister
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Persists-then-removes entities tagged [PersistAndRemove] (added on disconnect). The snapshot is taken
 * here, before the destroy; the write goes through [EntityWriteBehind], off the tick and in order with
 * every other write about the same owner.
 */
@SpringComponent
@Order(90)
class PersistAndRemoveSystem(
  private val writeBehind: EntityWriteBehind,
) : System {

  override val reads: ComponentClassSet = setOf(
    PersistAndRemove::class, Master::class, Account::class, Position::class,
    Level::class, SkillPoints::class, StatusPoints::class, BaseStatusValues::class,
    Health::class, EntityVisual::class, GroundItemStack::class, StatusEffects::class,
  ) + MasterEntityPersister.SNAPSHOT_READS

  override fun update(world: World, deltaTime: Float) {
    val toRemove = mutableListOf<EntityId>()
    world.query(PersistAndRemove::class).each { id -> toRemove.add(id) }
    if (toRemove.isEmpty()) return

    writeBehind.persist(world, toRemove)
    toRemove.forEach(world::destroy)
  }
}

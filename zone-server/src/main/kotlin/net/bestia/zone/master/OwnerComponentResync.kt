package net.bestia.zone.master

import net.bestia.zone.battle.ecs.exp.Exp
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.OwnerCombatStats
import net.bestia.zone.battle.ecs.status.SkillPoints
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.ecs.status.StatusPoints
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.place.ecs.Place
import net.bestia.zone.util.EntityId

/**
 * Re-pushes every owner-only component the player's own windows are built from. Each is pushed only on
 * change, so an entity whose values have settled never sends them again: not to a client that was still
 * loading its scene, and not to one that just took control of it.
 *
 * Owner-only and no others: [net.bestia.zone.engine.EntitySnapshotBuilder] re-announces the public half.
 * The list is opt-in rather than a scan over every [net.bestia.zone.sync.Dirtyable], because re-sending is
 * not harmless for all of them: a repeated `LogoutIntent`, for one, restarts the client's logout countdown.
 */
object OwnerComponentResync {

  fun resync(world: World, entityId: EntityId) {
    world.get(entityId, Health::class)?.markDirty()
    world.get(entityId, Mana::class)?.markDirty()
    world.get(entityId, Stamina::class)?.markDirty()
    world.get(entityId, CarryCapacity::class)?.markDirty()
    world.get(entityId, Exp::class)?.markDirty()
    world.get(entityId, Level::class)?.markDirty()
    world.get(entityId, StatusValues::class)?.markDirty()
    world.get(entityId, BaseStatusValues::class)?.markDirty()
    world.get(entityId, OwnerCombatStats::class)?.markDirty()
    world.get(entityId, StatusPoints::class)?.markDirty()
    world.get(entityId, SkillPoints::class)?.markDirty()
    world.get(entityId, Equipment::class)?.markDirty()
    world.get(entityId, Place::class)?.markDirty()
  }
}

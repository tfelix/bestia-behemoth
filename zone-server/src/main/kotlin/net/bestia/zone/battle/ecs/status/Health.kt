package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.status.CurMax
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.EntitySMSG

class Health(
  current: Int,
  max: Int
) : CurMax(current, max), Component, Dirtyable {

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return HealthComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    // Mobs have no owner: HP stays visible to everyone nearby.
    return if (world.has(entityId, Account::class)) SyncTargets.OwnerAndParty else SyncTargets.PublicInRange
  }
}
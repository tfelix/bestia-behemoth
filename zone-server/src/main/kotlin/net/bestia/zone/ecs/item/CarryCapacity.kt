package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.battle.status.CurMax

/**
 * Tracks carried inventory weight (current) against the weight limit derived from
 * Attributes/Level (max). [lastKnownStrength]/[lastKnownVitality]/[lastKnownLevel] let
 * [CarryCapacitySystem] skip recomputing max every tick.
 */
class CarryCapacity(
  current: Int,
  max: Int,
) : CurMax(current, max), Component {

  var lastKnownStrength: Int = -1
  var lastKnownVitality: Int = -1
  var lastKnownLevel: Int = -1

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return CarryCapacityComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}

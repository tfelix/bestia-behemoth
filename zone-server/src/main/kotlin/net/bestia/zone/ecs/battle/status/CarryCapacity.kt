package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.battle.status.CurMax

/**
 * Tracks carried inventory weight (current) against the weight limit derived from
 * [Attributes]/[net.bestia.zone.ecs.battle.level.Level] (max). Both values are cached here and
 * only recomputed by [CarryCapacitySystem] when their inputs actually change, rather than every
 * tick - [lastKnownStrength]/[lastKnownVitality]/[lastKnownLevel] are the bookkeeping used to
 * detect that.
 */
class CarryCapacity(
  current: Int,
  max: Int
) : CurMax(), Component, Dirtyable {
  private var dirty = true

  var lastKnownStrength: Int = -1
  var lastKnownVitality: Int = -1
  var lastKnownLevel: Int = -1

  override var current: Int
    get() = super.current
    set(value) {
      val oldValue = super.current
      super.current = value
      if (super.current != oldValue) {
        dirty = true
      }
    }

  override var max: Int
    get() = super.max
    set(value) {
      val oldValue = super.max
      super.max = value
      if (super.max != oldValue) {
        dirty = true
      }
    }

  init {
    this.max = max
    this.current = current
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
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

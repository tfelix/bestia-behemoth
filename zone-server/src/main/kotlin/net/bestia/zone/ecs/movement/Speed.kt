package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.ecs.movement.SpeedSMSG

data class Speed(
  private var _speed: Float = 2.5f
) : Component, Dirtyable {

  private var dirty: Boolean = true

  var speed: Float
    get() = _speed
    set(value) {
      if (_speed != value) {
        _speed = value
        dirty = true
      }
    }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return SpeedSMSG(
      entityId = entityId,
      speed = speed
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

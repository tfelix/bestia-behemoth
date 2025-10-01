package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG
import net.bestia.zone.message.entity.SpeedSMSG

data class Speed(
  private var _speed: Float = 1.0f
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
}

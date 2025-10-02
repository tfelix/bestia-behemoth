package net.bestia.zone.ecs.status

import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG

data class Exp(
  private var _value: Int = 0
) : Component, Dirtyable {

  var value: Int
    get() = _value
    set(newValue) {
      if (_value != newValue) {
        _value = newValue
        dirty = true
      }
    }

  private var dirty = true

  override fun isDirty(): Boolean = dirty

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return ExpSMSG(entityId = entityId, exp = value)
  }
}

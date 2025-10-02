package net.bestia.zone.ecs.status

import net.bestia.zone.ecs.Component
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.message.entity.EntitySMSG

class Level(
  level: Int
) : Component, Dirtyable {
  var level: Int = level
    private set(value) {
      dirty = true
      field = value
    }

  private var dirty = true

  fun inc() {
    level += 1
  }

  override fun isDirty(): Boolean {
    return dirty
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return LevelSMSG(entityId = entityId, level = level)
  }
}
package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

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

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return ExpComponentSMSG(entityId = entityId, exp = value)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}

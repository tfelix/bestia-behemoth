package net.bestia.zone.ecs.battle.level

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

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

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return LevelComponentSMSG(entityId = entityId, level = level)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
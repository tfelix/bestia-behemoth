package net.bestia.zone.battle.ecs.level

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG

class Level(
  level: Int
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var level: Int = level
    private set(value) {
      markDirty()
      field = value
    }

  fun inc() {
    level += 1
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return LevelComponentSMSG(entityId = entityId, level = level)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
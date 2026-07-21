package net.bestia.zone.ecs.movement

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.ecs.movement.SpeedSMSG

data class Speed(
  private var _speed: Float = 2.5f,
  /**
   * The unbuffed speed, set once at spawn and never touched by buffs. [speed] is the effective,
   * synced value - recomputed from this by
   * `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem` whenever an active status
   * effect's script mutates `StatusValueRecalcContext.speed`.
   */
  val baseSpeed: Float = _speed
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

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return SpeedSMSG(
      entityId = entityId,
      speed = speed
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

package net.bestia.zone.ecs.entity

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

data class Animation(
  private var _currentAnimation: AnimationKind = AnimationKind.IDLE
) : Component, Dirtyable {

  private var dirty: Boolean = true

  var currentAnimation: AnimationKind
    get() = _currentAnimation
    set(value) {
      if (_currentAnimation != value) {
        _currentAnimation = value
        dirty = true
      }
    }

  enum class AnimationKind {
    IDLE,
    WALK
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
    return AnimationSMSG(
      entityId = entityId,
      currentAnimation = currentAnimation
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

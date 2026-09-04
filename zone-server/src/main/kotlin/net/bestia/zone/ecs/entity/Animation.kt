package net.bestia.zone.ecs.entity

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * A pose the client could not work out for itself, derived every tick from the current plan step by
 * [net.bestia.zone.ai.ecs.AiActSystem].
 *
 * Deliberately narrow, for the reason [net.bestia.zone.ai.core.action.Posture] gives: anything an observer can
 * read off ordinary components does not belong here. Walking is the client's own, off its movement prediction.
 */
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
    /** Nothing overriding: the client's own walk/idle heuristic owns the pose. */
    IDLE,

    /** Lying down asleep. */
    SLEEP
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
    return AnimationSMSG(
      entityId = entityId,
      currentAnimation = currentAnimation
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

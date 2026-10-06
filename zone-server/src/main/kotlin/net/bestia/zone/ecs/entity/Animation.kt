package net.bestia.zone.ecs.entity

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
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
  override val dirtyFlag = DirtyFlag()

  var currentAnimation: AnimationKind
    get() = _currentAnimation
    set(value) {
      if (_currentAnimation != value) {
        _currentAnimation = value
        markDirty()
      }
    }

  enum class AnimationKind {
    /** Nothing overriding: the client's own walk/idle heuristic owns the pose. */
    IDLE,

    /** Lying down asleep. */
    SLEEP
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return AnimationSMSG(
      entityId = entityId,
      currentAnimation = currentAnimation
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

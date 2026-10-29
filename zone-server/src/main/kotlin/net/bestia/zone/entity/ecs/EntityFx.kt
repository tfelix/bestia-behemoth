package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * A short visual effect on this entity's body that the server times, such as a teleport's vanish.
 *
 * A component rather than a one-shot message, so an observer who comes into view while it plays gets it with
 * the entity's snapshot and sees the body appear rather than pop in. [EntityFxSystem] takes it off once it has
 * played.
 */
class EntityFx(
  val kind: Kind,
  val durationMs: Int,
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  var secondsLeft: Float = durationMs / 1000f

  enum class Kind {
    WING_VANISH,
    WING_APPEAR,
    HOME_VANISH,
    HOME_APPEAR,
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG =
    EntityFxSMSG(entityId = entityId, kind = kind, durationMs = durationMs)

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

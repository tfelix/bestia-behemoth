package net.bestia.zone.ecs.movement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.SpatiallyIndexed
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * Where an entity stands, in whole voxels, plus the movement bookkeeping that gets it there.
 *
 * Ordinary writes through [x]/[y]/[z] mark it for sync, as any component's do. A tile step along a [Path]
 * goes through [stepTo] instead, which does not, and [MoveSystem] publishes one step in
 * [MoveSystem.POSITION_RESYNC_STEPS] - the client's prediction is what draws the walk, so the resync is
 * insurance against a stall or a lost message rather than the mechanism.
 *
 * [stepTo] is a separate mutator rather than [MoveSystem] clearing the flag afterwards, because clearing it
 * would also swallow a write another system made in the same tick: `ChunkStreamSystem`'s GM teleport sets a
 * position and removes the path, and the removal is deferred to the end of the tick.
 */
data class Position(
  private var _x: Long,
  private var _y: Long,
  private var _z: Long,
  /**
   * How far into the step towards the next waypoint this entity has travelled, in metres.
   *
   * Metres rather than a fraction of the step, because a cardinal step is 1 m long and a diagonal one is
   * sqrt(2): a fraction would have to be rescaled every time the next step changed direction. Never synced -
   * the client interpolates the sub-tile part itself, from the path and the speed.
   */
  var stepProgress: Float = 0f
) : Component, Dirtyable, SpatiallyIndexed {
  override val dirtyFlag = DirtyFlag()
  override val movedFlag = DirtyFlag()

  var x: Long
    get() = _x
    set(value) {
      if (_x != value) {
        _x = value
        markDirty()
        movedFlag.set()
      }
    }

  var y: Long
    get() = _y
    set(value) {
      if (_y != value) {
        _y = value
        markDirty()
        movedFlag.set()
      }
    }

  var z: Long
    get() = _z
    set(value) {
      if (_z != value) {
        _z = value
        markDirty()
        movedFlag.set()
      }
    }

  /** Tile steps since this position was last published; [MoveSystem]'s counter, and never sent. */
  var stepsSinceSync: Int = 0

  /**
   * Whether this position has changed since the area-of-interest index last saw it.
   *
   * A second flag rather than a reuse of the dirty one, because **the index is not the wire**: every step has
   * to be indexed even though only one in [MoveSystem.POSITION_RESYNC_STEPS] is published, since
   * `AreaOfInterestService` answers what an area effect hits, what a creature can see and what a skill can
   * target. Cleared by [net.bestia.zone.engine.ZoneEngine] once it has re-indexed the entity.
   */
  val moved: Boolean
    get() = movedFlag.isSet

  fun clearMoved() {
    movedFlag.clear()
  }

  /** Moves the entity one tile along its path **without** marking it for sync; [MoveSystem] only. */
  fun stepTo(x: Long, y: Long, z: Long) {
    if (_x == x && _y == y && _z == z) return

    _x = x
    _y = y
    _z = z
    movedFlag.set()
  }

  fun toVec3L(): Vec3L {
    return Vec3L(x, y, z)
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return PositionSMSG(
      entityId = entityId,
      position = Vec3L(x, y, z)
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange

  companion object {
    fun fromVec3(pos: Vec3L): Position {
      return Position(
        pos.x,
        pos.y,
        pos.z
      )
    }
  }
}
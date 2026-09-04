package net.bestia.zone.ecs.movement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * Where an entity stands, in whole voxels, plus the movement bookkeeping that gets it there.
 *
 * ### A walking entity does not publish every tile
 *
 * Ordinary writes through [x]/[y]/[z] mark this for sync, as any component's do - a spawn, a teleport,
 * a respawn. A tile step taken along a [Path] goes through [stepTo] instead, which does not, and
 * [MoveSystem] publishes only one step in [MoveSystem.POSITION_RESYNC_STEPS]. Every step used to be
 * published: four a second per moving entity per observer, saying what the path had already said.
 *
 * The resync that remains is insurance, not the mechanism. The client's prediction is what draws the
 * walk; it can only drift by the latency of the path message and by the sub-tile [fraction] the server
 * was already carrying when the path arrived, neither of which grows with distance. What the resync
 * actually catches is a client that stalled or lost a message.
 *
 * [stepTo] is a separate mutator rather than [MoveSystem] clearing the dirty flag afterwards on
 * purpose: clearing it would also swallow a write some *other* system made in the same tick - the GM
 * teleport in `ChunkStreamSystem` sets a position and removes the path, and the removal is deferred to
 * the end of the tick, so `MoveSystem` can still step the same entity afterwards.
 */
data class Position(
  private var _x: Long,
  private var _y: Long,
  private var _z: Long,
  var fraction: Float = 0f
) : Component, Dirtyable {

  private var dirty: Boolean = true

  var x: Long
    get() = _x
    set(value) {
      if (_x != value) {
        _x = value
        dirty = true
        moved = true
      }
    }

  var y: Long
    get() = _y
    set(value) {
      if (_y != value) {
        _y = value
        dirty = true
        moved = true
      }
    }

  var z: Long
    get() = _z
    set(value) {
      if (_z != value) {
        _z = value
        dirty = true
        moved = true
      }
    }

  /**
   * Tile steps taken since this position was last published; [MoveSystem]'s counter, kept here beside
   * [fraction] because it is the same kind of thing - movement bookkeeping that is never sent.
   */
  var stepsSinceSync: Int = 0

  /**
   * Whether this position has changed since the area-of-interest index last saw it.
   *
   * Deliberately a second flag rather than a reuse of the dirty one: **the index is not the wire.**
   * A walking entity publishes one step in [MoveSystem.POSITION_RESYNC_STEPS], but every step has to
   * be indexed, because `AreaOfInterestService` is what answers who receives a broadcast, who an area
   * effect hits, what a creature can see and what a skill can target. Driving it off the sync flag
   * would have left all of those answering from a position up to eight tiles old.
   *
   * Cleared by [net.bestia.zone.ecs.ZoneEngine] once it has re-indexed the entity.
   */
  var moved: Boolean = true
    private set

  fun clearMoved() {
    moved = false
  }

  /**
   * Moves the entity one tile along its path **without** marking it for sync. See the note on the
   * class; only [MoveSystem] should call this.
   */
  fun stepTo(x: Long, y: Long, z: Long) {
    if (_x == x && _y == y && _z == z) return

    _x = x
    _y = y
    _z = z
    moved = true
  }

  fun toVec3L(): Vec3L {
    return Vec3L(x, y, z)
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
package net.bestia.zone.ecs.movement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Removable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * The waypoints an entity is walking, and the one message that tells observers about a walk.
 *
 * ### Sent when the path changes, not while it is walked
 *
 * [MoveSystem] consuming a waypoint does **not** dirty this component, and [removeFirst] deliberately
 * does not mark it. The client integrates the waypoints with the same arithmetic the server does
 * (`entity.gd`'s prediction says so in its own header), so the shrinking remainder is something it
 * already knows. Sending it anyway cost O(n^2) bytes for an n-tile walk - a click at the edge of the
 * view volume is ~176 tiles, which was some 200 kB per observer - and it actively broke the
 * prediction it was feeding: `update_path` re-anchors on the client's current position and resets its
 * progress, so a step's `Position` then arrived to be looked up in an already-shortened waypoint list
 * and, whenever the client was more than half a tile behind, missed it and took the "server is off our
 * predicted path" branch. Snap, stop, idle, restart - four times a second.
 *
 * ### Its removal is the stop notification
 *
 * [Removable], so coming off an entity re-sends this message with an empty path and the position the
 * entity stopped at - see [toRemovedMessage]. Seven places remove it and only one of them is a walk finishing: combat, sleep
 * ([net.bestia.zone.ai.bt.leaves.Sleep]), death, a client stop command and a GM teleport all cut a
 * walk short, and none of them dirty [Position] on the way. Before this was `Removable` those stops
 * were invisible on the wire, so every observer walked the entity on to the end of the path it was no
 * longer following and left it there - a mob that lay down mid-walk slept a tile or more away from
 * where the server had it, indefinitely, because a stationary entity's position never goes dirty
 * again.
 */
data class Path(
  private var _path: MutableList<Vec3L>
) : Component, Removable {

  init {
    require(_path.isNotEmpty()) { "Path must not be empty on creation." }
  }

  private var dirty: Boolean = true

  /**
   * Whether the waypoints' vertical has been checked against the terrain yet.
   *
   * A player-supplied path arrives with a vertical the *client* invented: `path_calculator.gd` interpolates it
   * linearly between the two endpoints and says in its own docstring that it ignores terrain. Correcting the
   * entity's [Position] as it walks is not enough on its own, because this component is synced to every client in
   * range and `entity.gd` interpolates the rendered position *along these waypoints* between position updates - so
   * an unresolved path makes every observer draw the walk along that straight line through the hillside.
   *
   * [MoveSystem] resolves it on the tick it first sees the path, which is before the component sync runs.
   */
  var groundResolved: Boolean = false
    private set

  val path: List<Vec3L>
    get() = _path.toList()

  /**
   * Hands out the next waypoint. Does **not** dirty the component - see the note on the class: what
   * observers need is the path, once, not the remainder of it sixty-odd times.
   */
  fun removeFirst(): Vec3L = _path.removeFirst()

  fun setPath(newPath: List<Vec3L>) {
    if (_path != newPath) {
      _path.clear()
      _path.addAll(newPath)
      dirty = true
      groundResolved = false
    }
  }

  fun addPathPoint(point: Vec3L) {
    _path.add(point)
    dirty = true
    groundResolved = false
  }

  /**
   * Replaces every waypoint's vertical with the ground's, and marks the path resolved.
   *
   * A waypoint whose column has no answer - off the grid, or no world yet - keeps the vertical it arrived with,
   * on the same reasoning as [MoveSystem]'s per-step fallback: moving somewhere approximately right beats
   * refusing to move.
   */
  fun resolveGround(groundAt: (Vec3L) -> Long?) {
    for (i in _path.indices) {
      val point = _path[i]
      val z = groundAt(point) ?: continue

      if (z != point.z) {
        _path[i] = Vec3L(point.x, point.y, z)
        dirty = true
      }
    }

    groundResolved = true
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
    return PathSMSG(
      entityId = entityId,
      path = path
    )
  }

  /**
   * The stop notification: an empty path plus where the entity actually halted. Same message type as
   * the walk on purpose, which is what [Removable] buys - the client's `update_path` reads an empty
   * path as "stop here" and needs no second thing to dispatch on.
   *
   * The position is read live rather than remembered from the last step, because two of the removers
   * move the entity in the same breath: `RespawnSystem` puts it at its respawn point and
   * `ChunkStreamSystem` teleports it. A tile stamped during the walk would have snapped every observer
   * back to where the entity died.
   */
  override fun toRemovedMessage(world: World, entityId: EntityId): EntitySMSG {
    return PathSMSG(
      entityId = entityId,
      path = emptyList(),
      stopPosition = world.get(entityId, Position::class)?.toVec3L()
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

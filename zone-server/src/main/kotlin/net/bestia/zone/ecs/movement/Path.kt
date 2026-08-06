package net.bestia.zone.ecs.movement

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG

data class Path(
  private var _path: MutableList<Vec3L>
) : Component, Dirtyable {

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

  fun removeFirst(): Vec3L {
    dirty = true

    return _path.removeFirst()
  }

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

  fun clearPath() {
    if (_path.isNotEmpty()) {
      _path.clear()
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
    return PathSMSG(
      entityId = entityId,
      path = path
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}

package net.bestia.zone.movement.ecs

import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.sync.Removable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.message.EntitySMSG

/**
 * The waypoints an entity is walking, and the one message that tells observers about a walk.
 *
 * Sent when the path *changes*, not while it is walked: the client follows these waypoints with the same
 * arithmetic [MoveSystem] uses, so the shrinking remainder is nothing it does not already hold.
 *
 * Its removal is the stop notification - see [toRemovedMessage]. That matters because only one of the seven
 * places that remove a path is a walk finishing; combat, sleep, death, a client stop and a GM teleport all cut
 * one short, and none of them dirties [Position].
 */
data class Path(
  private var _path: MutableList<Vec3L>
) : Component, Removable {
  override val dirtyFlag = DirtyFlag()

  init {
    require(_path.isNotEmpty()) { "Path must not be empty on creation." }
  }

  /**
   * Whether the waypoints' vertical has been checked against the terrain yet.
   *
   * A client-supplied path arrives with a vertical `path_calculator.gd` interpolated linearly, ignoring
   * terrain - and observers draw the walk *along these waypoints*, so correcting only [Position] would leave
   * every one of them running it through the hillside. [MoveSystem] resolves it before the sync runs.
   */
  var groundResolved: Boolean = false
    private set

  /**
   * Whether the walk starts over from the entity's tile, which [MoveSystem] answers by dropping the progress made
   * into the step it was on. True for a new path and a replaced one.
   *
   * An appended leg or a [rerouteAfter] keeps the step under way. Dropping it there stood the entity back on the
   * tile it was leaving, a part of a step lost on every leg - and the client, walking on, read that as the server
   * falling behind.
   */
  var restartsStep: Boolean = true
    private set

  /** [MoveSystem] has started the walk over. */
  fun stepRestarted() {
    restartsStep = false
  }

  /**
   * How far past its last reached tile the entity stands, 0..1, refreshed by [MoveSystem] each tick.
   *
   * On the wire so a client told about a walk already under way joins it where the entity actually is.
   * Writing it does not dirty the component: it changes every tick and is only read once something else has
   * decided to send the path.
   */
  var startOffset: Float = 0f

  val path: List<Vec3L>
    get() = _path.toList()

  /**
   * Whether every waypoint has been consumed.
   *
   * Not `path.isEmpty()`: that getter copies the whole list, and [MoveSystem] asks this once per tick per
   * walking entity and again after every tile it steps.
   */
  val isEmpty: Boolean
    get() = _path.isEmpty()

  /** The waypoint being walked towards. Only meaningful while this is not [isEmpty]. */
  val next: Vec3L
    get() = _path.first()

  /** Hands out the next waypoint. Deliberately does not dirty the component - see the class note. */
  fun removeFirst(): Vec3L = _path.removeFirst()

  fun setPath(newPath: List<Vec3L>) {
    if (_path != newPath) {
      _path.clear()
      _path.addAll(newPath)
      markDirty()
      groundResolved = false
      restartsStep = true
    }
  }

  /** The last waypoint, or null once the walk is spent. */
  val lastWaypoint: Vec3L?
    get() = _path.lastOrNull()

  fun appendPath(points: List<Vec3L>) {
    _path.addAll(points)
    markDirty()
    groundResolved = false
  }

  /**
   * Where the walk first crosses [tile]'s column, or -1. Horizontal, because a waypoint's height is the ground's
   * rather than the one it was sent with - see [resolveGround].
   */
  fun indexOfColumn(tile: Vec3L): Int = _path.indexOfFirst { it.x == tile.x && it.y == tile.y }

  /**
   * Keeps the waypoints up to and including [index] and walks on along [points] instead of the rest: a new
   * destination picked mid-walk, joined where the old route passes. The step under way is kept, see [restartsStep].
   *
   * Announced even when the route comes out the same, unlike [setPath]: the client that picked it is walking it
   * already and waits for this answer, and one that never comes makes it give the walk up - see `entity.gd`.
   */
  fun rerouteAfter(index: Int, points: List<Vec3L>) {
    require(index in _path.indices) { "No waypoint $index to reroute after, the walk has ${_path.size}." }

    _path.subList(index + 1, _path.size).clear()
    _path.addAll(points)
    markDirty()
    groundResolved = false
  }

  /**
   * Replaces every waypoint's vertical with the ground's, and marks the path resolved.
   *
   * A waypoint whose column has no answer - off the grid, or no world yet - keeps the vertical it arrived
   * with: moving somewhere approximately right beats refusing to move.
   */
  fun resolveGround(groundAt: (Vec3L) -> Long?) {
    for (i in _path.indices) {
      val point = _path[i]
      val z = groundAt(point) ?: continue

      if (z != point.z) {
        _path[i] = Vec3L(point.x, point.y, z)
        markDirty()
      }
    }

    groundResolved = true
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return PathSMSG(
      entityId = entityId,
      path = path,
      startOffset = startOffset
    )
  }

  /**
   * The stop notification: an empty path plus where the entity actually halted. Same message type as the walk,
   * so the client needs no second thing to dispatch on.
   *
   * The position is read live rather than remembered from the last step, because two removers move the entity
   * in the same breath - `RespawnSystem` and `ChunkStreamSystem`'s teleport.
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

package net.bestia.zone.ecs.movement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.TickBuckets
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.spoor.ActorSignatureSystem
import kotlin.math.sqrt
import org.springframework.stereotype.Component as SpringComponent

/**
 * Advances entities along their [Path], charging each step the ground it actually covers.
 *
 * ### A diagonal step is longer, so it takes longer
 *
 * This used to add `speed * deltaTime` to a counter and pop a waypoint whenever it passed 1.0 - every
 * waypoint costing the same whichever way it went. A diagonal waypoint is sqrt(2) m away, so diagonal
 * movement ran at 4*sqrt(2) m/s against 4 m/s cardinal: the 41% difference players notice. `entity.gd`
 * mirrored the same arithmetic deliberately, so the two agreed with each other and both disagreed with the
 * world.
 *
 * The pathfinder never did. `LocalWalkGraph.cost` has always charged sqrt(2) for a diagonal, so A* was
 * optimising for a cost model movement did not use and NPCs preferred straight routes that took longer.
 *
 * `z` is recomputed here from the heightfield rather than taken from the waypoint, so the character follows
 * the ground and a client cannot choose its own altitude - `path_calculator.gd` interpolates the vertical
 * linearly and ignores terrain. One column lookup per tile stepped, not per tick.
 *
 * A walk puts one [Path] message on the wire when the path is set and one when it comes off the entity.
 * Neither the waypoint consumed nor the [Position] written is published per step; every
 * [POSITION_RESYNC_STEPS]th step is, as insurance against a client that stalled or lost a message.
 */
@SpringComponent
class MoveSystem(
  private val ground: GroundHeight,
  private val trample: GroundTrample,
  private val walkQuery: LocalWalkQuery,
) : System {
  override val phase = Phase.MOVEMENT
  override val after = setOf(ActorSignatureSystem::class)

  override val reads: ComponentClassSet = setOf(Speed::class, Dead::class, Account::class)
  override val writes: ComponentClassSet = setOf(Position::class, Path::class, CoarseMovement::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Speed::class, Path::class).each { id ->
      // A player body stays where it fell. PlayerDeathSystem drops the path on the tick the entity
      // dies, so this only catches one added afterwards.
      if (world.has(id, Dead::class)) return@each

      val position = get<Position>()
      val speed = get<Speed>()
      val movementPath = get<Path>()

      // Before the component sync, because observers render the walk along these waypoints - see
      // Path.groundResolved.
      if (!movementPath.groundResolved) {
        movementPath.resolveGround { ground.standingZAt(it) }

        // A fresh path is a fresh walk, so the resync counter starts over with it.
        position.stepsSinceSync = 0

        // A fresh path starts from this tile, not from however far into its own step the walk before it had
        // got. `entity.gd` anchors its prediction on the entity's current position when the path arrives, so
        // carried-over progress is a disagreement from the very first step.
        position.stepProgress = 0f
      }

      if (movementPath.isEmpty) {
        world.remove(id, Path::class)
        position.stepProgress = 0f
        return@each
      }

      // Ground covered since the last call, in metres.
      val elapsed = coarseElapsed(world, id, deltaTime) ?: return@each
      position.stepProgress += speed.speed * elapsed

      // `!isEmpty` is a real condition rather than belt and braces: an entity faster than one tile per tick
      // crosses several tiles in one update, and a walk that ran out of waypoints with progress still left
      // would go round again and take `removeFirst` off an empty list.
      // A player's path was only checked where the ground was loaded when it arrived; NPC paths come from the
      // local pathfinder, which already checks every step.
      val checkSteps = world.has(id, Account::class)
      var refused = false
      var stepped = 0
      while (!movementPath.isEmpty) {
        val nextPoint = movementPath.next
        val stepLength = groundDistance(position, nextPoint)

        // A step is taken only once its own ground has been paid for, so a diagonal costs the sqrt(2) it
        // spans instead of the 1 a waypoint count charged it.
        if (position.stepProgress < stepLength) break

        position.stepProgress -= stepLength
        movementPath.removeFirst()

        if (checkSteps && refusesStep(position.toVec3L(), nextPoint)) {
          refused = true
          break
        }

        // Read before the step, because the tile being left is what gives the footfall below its heading.
        val fromX = position.x
        val fromY = position.y

        // The waypoint's z is the fallback, reached only for a column with no height - off the grid, or a
        // world not generated yet.
        val z = ground.standingZAt(nextPoint) ?: nextPoint.z

        // Not the x/y/z setters: a step is not published, see Position.stepTo.
        position.stepTo(nextPoint.x, nextPoint.y, z)
        stepped++

        // Per stepped tile rather than per tick, and inside the loop for the reason the loop exists: an
        // overrunning tick arrives here as several tiles at once, and a trail that skipped them would be
        // dotted exactly when the server was struggling.
        trample.steppedOn(id, fromX, fromY, nextPoint.x, nextPoint.y)

        LOG.trace { "Entity $id on $nextPoint" }
      }

      // The client drew the walk through, so it is told where the entity really stopped.
      if (refused) {
        world.remove(id, Path::class)
        position.stepProgress = 0f
        position.markDirty()
        return@each
      }

      // Arrival, checked after the loop rather than inside it so that the removal and the loop's exit are the
      // same decision. The removal is the stop notification and reads this position off the entity, so the
      // arrival needs no position sync of its own - see Path.toRemovedMessage. The progress goes with it:
      // whatever is left over belongs to a walk that is finished, and carrying it into the next path would
      // spend the first tile of that one before it started.
      if (movementPath.isEmpty) {
        world.remove(id, Path::class)
        position.stepProgress = 0f
      }

      // Every tick, stepped or not, so a path sent to a late observer says where the entity is now. As a
      // share of the step being walked rather than in metres: that is what the client scales its own
      // first segment by, and it is the one segment whose length the client cannot infer from Position.
      movementPath.startOffset = if (movementPath.isEmpty) {
        0f
      } else {
        (position.stepProgress / groundDistance(position, movementPath.next)).coerceIn(0f, 1f)
      }

      if (stepped == 0) return@each

      position.stepsSinceSync += stepped
      if (position.stepsSinceSync >= POSITION_RESYNC_STEPS) {
        position.stepsSinceSync = 0
        position.markDirty()
      }
    }
  }

  private fun refusesStep(from: Vec3L, to: Vec3L): Boolean {
    return walkQuery.isResident(from) && walkQuery.isResident(to) && !walkQuery.canStep(from, to)
  }

  /**
   * The time this entity is moved by now: one tick, or for a [CoarseMovement] walker everything since its last
   * turn - or null when this is not its turn.
   */
  private fun coarseElapsed(world: World, id: EntityId, deltaTime: Float): Float? {
    val coarse = world.get(id, CoarseMovement::class) ?: return deltaTime
    val tick = world.tickCount

    if (!TickBuckets.isDue(tick, id, coarse.everyTicks)) return null

    val ticks = if (coarse.lastTick < 0) 1L else tick - coarse.lastTick
    coarse.lastTick = tick

    return ticks * deltaTime
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * How many tile steps a walking entity takes between authoritative position pushes.
     *
     * Eight is two seconds at the default speed of four tiles a second: cheap enough to ignore (29 bytes per
     * observer, ~15 B/s) and close enough that a client which lost the path message is not wrong for long.
     * It is not what keeps the walk in step - the shared integrator is - so it scales with nothing.
     */
    const val POSITION_RESYNC_STEPS = 8

    /**
     * Ground distance from where an entity is to a waypoint, in metres: 1 for a cardinal step, sqrt(2) for a
     * diagonal one.
     *
     * Not [net.bestia.zone.geometry.Vec3L.distance], which truncates to a whole number and would therefore
     * report a diagonal step as 1 - which is the bug, not the fix.
     *
     * Horizontal, matching what the client measures its own interpolation along. A walkable slope does not
     * make an entity slower: `LocalWalkGraph.cost` prices climbing as a *preference* between legal routes
     * rather than as extra ground to cover.
     */
    private fun groundDistance(from: Position, to: Vec3L): Float {
      val dx = (to.x - from.x).toDouble()
      val dy = (to.y - from.y).toDouble()

      return sqrt(dx * dx + dy * dy).toFloat()
    }
  }
}

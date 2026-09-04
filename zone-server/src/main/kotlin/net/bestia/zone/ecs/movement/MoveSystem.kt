package net.bestia.zone.ecs.movement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Advances entities along their [Path], one tile per `fraction` rollover.
 *
 * ### The vertical coordinate is the server's, not the path's
 *
 * A path's `z` used to be copied straight out of the waypoint, and for a player-supplied path that meant the
 * client decided its own elevation. `path_calculator.gd` produces one by **linearly interpolating** between the
 * start and the destination - it says so itself, and it ignores terrain entirely - so walking across a hill sank
 * the character into the slope and floating it over the dip beyond, by however much the straight line missed the
 * ground by. `MoveActiveEntityHandler` validated only `x` and `y`, so nothing caught it.
 *
 * Recomputing `z` here from the heightfield fixes both halves at once: the character follows the ground, and a
 * client cannot choose its own altitude. It costs one column lookup per tile stepped, not per tick.
 *
 * ### What a walk puts on the wire
 *
 * One [Path] message when the path is set, and one when it comes off the entity. Neither the waypoint it
 * consumes nor the [Position] it writes is published per step: the client integrates the same waypoints with
 * the same arithmetic, so both were telling it what it already knew - see the notes on [Path] and [Position]
 * for what that cost and what it broke. Every [POSITION_RESYNC_STEPS]th step is published as insurance
 * against a client that stalled or lost a message.
 */
@SpringComponent
@Order(40)
class MoveSystem(private val ground: GroundHeight) : System {

  override val reads: ComponentClassSet = setOf(Speed::class, Dead::class)
  override val writes: ComponentClassSet = setOf(Position::class, Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Speed::class, Path::class).each { id ->
      // A player body stays where it fell. PlayerDeathSystem drops the path on the tick the entity
      // dies, so this only catches one added afterwards.
      if (world.has(id, Dead::class)) return@each

      val position = get<Position>()
      val speed = get<Speed>()
      val movementPath = get<Path>()

      // Before advancing, and before the component sync: the waypoints are what every observer renders the walk
      // along, so correcting only `position` would fix where the entity *is* and leave the path it appears to
      // take running through the hillside. See Path.groundResolved.
      if (!movementPath.groundResolved) {
        movementPath.resolveGround { ground.standingZAt(it) }

        // A fresh path (or a re-route, which clears the flag again) is a fresh walk, so the resync
        // counter starts over with it rather than carrying a remainder in from the last one.
        position.stepsSinceSync = 0
      }

      if (movementPath.path.isEmpty()) {
        world.remove(id, Path::class)
        position.fraction = 0f
        return@each
      }

      // calculate the movement advances of the entity since the last call.
      position.fraction += speed.speed * deltaTime

      // entity has moved more than one tile so its position can be updated.
      var stepped = 0
      while (position.fraction > 1) {
        val nextPoint = movementPath.removeFirst()

        // The waypoint's own z is the fallback rather than the answer: it is only reached when the column has no
        // height to report, which means off the grid or a world that is not generated yet. Keeping the old
        // behaviour there is better than refusing to move.
        val z = ground.standingZAt(nextPoint) ?: nextPoint.z

        // Deliberately not the x/y/z setters: a step is not published, see Position.stepTo.
        position.stepTo(nextPoint.x, nextPoint.y, z)
        stepped++

        LOG.trace { "Entity $id on $nextPoint" }

        if (movementPath.path.isEmpty()) {
          // The removal is the stop notification, and it reads this position off the entity - so the
          // arrival needs no position sync of its own. See Path.toRemovedMessage.
          world.remove(id, Path::class)
        }

        position.fraction -= 1
      }

      if (stepped == 0) return@each

      position.stepsSinceSync += stepped
      if (position.stepsSinceSync >= POSITION_RESYNC_STEPS) {
        position.stepsSinceSync = 0
        position.markDirty()
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * How many tile steps a walking entity takes between authoritative position pushes.
     *
     * Eight is two seconds at the default speed of four tiles a second. There is nothing special about it
     * beyond being far enough apart to be cheap (29 bytes per observer, so ~15 B/s) and close enough
     * together that a client which lost the path message is not wrong for long. It is not what keeps the
     * walk in step - the shared integrator is - so it does not need to scale with anything.
     */
    const val POSITION_RESYNC_STEPS = 8
  }
}

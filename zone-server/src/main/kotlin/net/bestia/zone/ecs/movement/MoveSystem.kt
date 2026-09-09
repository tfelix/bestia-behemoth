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
 * `z` is recomputed here from the heightfield rather than taken from the waypoint, so the character follows
 * the ground and a client cannot choose its own altitude - `path_calculator.gd` interpolates the vertical
 * linearly and ignores terrain. One column lookup per tile stepped, not per tick.
 *
 * A walk puts one [Path] message on the wire when the path is set and one when it comes off the entity.
 * Neither the waypoint consumed nor the [Position] written is published per step; every
 * [POSITION_RESYNC_STEPS]th step is, as insurance against a client that stalled or lost a message.
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

      // Before the component sync, because observers render the walk along these waypoints - see
      // Path.groundResolved.
      if (!movementPath.groundResolved) {
        movementPath.resolveGround { ground.standingZAt(it) }

        // A fresh path is a fresh walk, so the resync counter starts over with it.
        position.stepsSinceSync = 0

        // A fresh path starts from this tile, not from however far into its own step the walk before it had
        // got. `entity.gd` anchors its prediction on the entity's current position when the path arrives, so
        // a carried-over fraction is a disagreement from the very first step.
        position.fraction = 0f
      }

      // calculate the movement advances of the entity since the last call.
      position.fraction += speed.speed * deltaTime

      var stepped = 0

      // Emptiness bounds the loop rather than being handled inside it: the removal below is deferred to the
      // end of the tick, so a path drained mid-loop is still attached on the next turn and `removeFirst`
      // would throw - taking every later wave and the tick's whole component sync down with it. A tick long
      // enough to cross several tiles does legitimately cross several tiles.
      while (position.fraction >= 1 && !movementPath.isEmpty) {
        val nextPoint = movementPath.removeFirst()

        // The waypoint's z is the fallback, reached only for a column with no height - off the grid, or a
        // world not generated yet.
        val z = ground.standingZAt(nextPoint) ?: nextPoint.z

        // Not the x/y/z setters: a step is not published, see Position.stepTo.
        position.stepTo(nextPoint.x, nextPoint.y, z)
        stepped++

        LOG.trace { "Entity $id on $nextPoint" }

        position.fraction -= 1
      }

      if (movementPath.isEmpty) {
        // The removal is the stop notification and reads this position off the entity, so the arrival
        // needs no position sync of its own. See Path.toRemovedMessage.
        world.remove(id, Path::class)

        // The walk is over, so there is no part-step left to stand in.
        position.fraction = 0f
      }

      // Every tick, stepped or not, so a path sent to a late observer says where the entity is now.
      movementPath.startOffset = position.fraction.coerceIn(0f, 1f)

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
     * Eight is two seconds at the default speed of four tiles a second: cheap enough to ignore (29 bytes per
     * observer, ~15 B/s) and close enough that a client which lost the path message is not wrong for long.
     * It is not what keeps the walk in step - the shared integrator is - so it scales with nothing.
     */
    const val POSITION_RESYNC_STEPS = 8
  }
}

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
      }

      if (movementPath.path.isEmpty()) {
        world.remove(id, Path::class)
        position.fraction = 0f
        return@each
      }

      // calculate the movement advances of the entity since the last call.
      position.fraction += speed.speed * deltaTime

      // entity has moved more than one tile so its position can be updated.
      while (position.fraction > 1) {
        val nextPoint = movementPath.removeFirst()
        position.x = nextPoint.x
        position.y = nextPoint.y

        // The waypoint's own z is the fallback rather than the answer: it is only reached when the column has no
        // height to report, which means off the grid or a world that is not generated yet. Keeping the old
        // behaviour there is better than refusing to move.
        position.z = ground.standingZAt(nextPoint) ?: nextPoint.z

        LOG.trace { "Entity $id on $nextPoint" }

        if (movementPath.path.isEmpty()) {
          world.remove(id, Path::class)
        }

        position.fraction -= 1
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

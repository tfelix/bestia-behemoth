package net.bestia.zoneserver.entity.movement

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.SpeedComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service
import kotlin.math.floor
import kotlin.math.sqrt

private val LOG = KotlinLogging.logger { }

/**
 * This manager holds references of currently moving entities and their movement
 * managing actors in order to control movement after it has been triggered. By
 * utilizing actor as the manager of the movement we need to check for possibly
 * triggered scripts/entities on the ways of this entity. Also there might be
 * generated input for AI entities.
 *
 * @author Thomas Felix
 */
@Service
class MovingService {

  /**
   * Calculates the next movement tick depending on the move speed. If -1 is
   * returned this means that the unit can no longer move (an error occurred
   * while calculating the movement) or the unit is considered to be static
   * and non movable.
   * The position mit be adjacent to the current position of the entity.
   *
   * @param entity The entity which wants to move.
   * @param newPos The new position.
   * @return The delay in ms for the next movement tick, or -1 if an error has
   * occurred.
   */
  fun getMoveDelayMs(entity: Entity, newPos: Vec3): Long {
    val walkspeed = entity.tryGetComponent(StatusComponent::class.java)?.statusBasedValues?.walkspeed
        ?: return -1
    val posComp = entity.tryGetComponent(PositionComponent::class.java)
        ?: return -1

    val d = posComp.position.getDistance(newPos)

    // Distance should be 1 or 2 (which means walking diagonally)
    val diagMult = when {
      d > 1 -> SQRT_TWO
      else -> 1.0
    }

    return floor(1 / TILES_PER_SECOND * 1000f * (1 / walkspeed) * diagMult).toLong()
  }

  /**
   * Sets the position of the given entity to a new point and performs all the
   * needed movement checks for triggering movement related effects. The moved
   * entity must have the [PositionComponent] otherwise it throws
   * [IllegalArgumentException].
   *
   * @param entity The entity to be moved.
   * @param newPos   The new position.
   * @return Updated position component
   */
  fun moveToPosition(entity: Entity, newPos: Vec3): PositionComponent {
    LOG.trace { "moveToPosition: Entity(${entity.id}) to pos: $newPos" }
    val positionComp = entity.getComponent(PositionComponent::class.java)

    return positionComp.copy(
        shape = positionComp.shape.moveTo(newPos),
        facing = Vec3(1, 0, 0)
    )
  }

  companion object {
    private const val TILES_PER_SECOND = 1.4f
    private val SQRT_TWO = sqrt(2.0)
  }
}

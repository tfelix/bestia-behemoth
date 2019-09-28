package net.bestia.zoneserver.entity.movement

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This service performs the movement of the Bestia it will also validate if
 * this movement is possible this means it will watch out the moved distance is
 * valid.
 *
 * @author Thomas Felix
 */
@Service
class MovingService2 {

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

    return Math.floor(1 / TILES_PER_SECOND * 1000f * (1 / walkspeed) * diagMult).toLong()
  }

  companion object {
    private const val TILES_PER_SECOND = 1.4f
    private val SQRT_TWO = Math.sqrt(2.0)
  }
}

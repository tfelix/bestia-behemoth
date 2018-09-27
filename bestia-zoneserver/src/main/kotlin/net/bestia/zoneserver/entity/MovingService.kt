package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.MoveComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.model.domain.Direction
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

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
class MovingService(
    private val entityService: EntityService,
    private val entityCollisionService: EntityCollisionService
) {

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
  fun getMoveDelayMs(entity: Entity, newPos: Point): Int {
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

    return Math.floor(1 / TILES_PER_SECOND * 1000f * (1 / walkspeed.speed) * diagMult).toInt()
  }

  /**
   * Sets the position of the given entity to a new point and performs all the
   * needed movement checks for triggering movement related effects. The moved
   * entity must have the [PositionComponent] otherwise it throws
   * [IllegalArgumentException].
   *
   * @param entity The entity to be moved.
   * @param newPos   The new position.
   */
  fun moveToPosition(entity: Entity, newPos: Point) {
    val positionComp = entity.getComponent(PositionComponent::class.java)
    val oldPos = positionComp.position
    val preMoveCollisions = entityCollisionService.getAllCollidingEntityIds(oldPos)

    positionComp.position = newPos
    positionComp.facing = getDirection(oldPos, newPos, positionComp.facing)

    val postMoveCollisions = entityCollisionService.getAllCollidingEntityIds(newPos).toMutableSet()
    postMoveCollisions.removeAll(preMoveCollisions)

    LOG.trace { "Moving entity $entity to postition: $newPos." }

    // TODO Check if a new collision has occurred and if necessary trigger scripts.

    entityService.updateComponent(positionComp)
  }

  /**
   * Calculates the direction of the entity which it is now facing. Usually an
   * entity faces the direction it moves. If old and new position are the
   * same, then the lastDirection is returned.
   *
   * @param oldPos The old position.
   * @param newPos The new position.
   * @return The direction the unit now faces.
   */
  private fun getDirection(oldPos: Point, newPos: Point, lastDirection: Direction): Direction {
    if (oldPos == newPos) {
      return lastDirection
    }

    val y = newPos.y - oldPos.y
    val x = newPos.x - oldPos.x

    return if (x == 0L && y > 0) {
      Direction.SOUTH
    } else if (x > 0 && y > 0) {
      Direction.SOUTH_EAST
    } else if (x > 0 && y == 0L) {
      Direction.EAST
    } else if (x > 0 && y < 0) {
      Direction.NORTH_EAST
    } else if (x == 0L && y < 0) {
      Direction.NORTH
    } else if (x < 0 && y < 0) {
      Direction.NORTH_WEST
    } else if (x < 0 && y == 0L) {
      Direction.WEST
    } else {
      Direction.SOUTH_WEST
    }
  }

  /**
   * This triggers a longer movement by using a path. This involves spinning
   * up an actor which will continuously update the movement of the entity
   * until an error occurs or the end of the path has been reached.
   *
   * @param entityId The entity to move.
   * @param path     The path to move along.
   */
  fun movePath(entity: Entity, path: List<Point>) {
    LOG.trace { "Moving entity $entity along path: $path" }

    // TODO Create a move component. Think if we need a proper ID for it.
    val mc = entityService.getComponentOrCreate(entityId, MoveComponent::class.java)
    mc.setPath(path)
    entityService.updateComponent(mc)
  }

  companion object {
    private const val TILES_PER_SECOND = 1.4f
    private val SQRT_TWO = Math.sqrt(2.0)
  }
}

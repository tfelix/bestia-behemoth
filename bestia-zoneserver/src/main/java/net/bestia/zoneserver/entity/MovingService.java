package net.bestia.zoneserver.entity;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.MoveComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.model.domain.Direction;
import net.bestia.model.entity.StatusBasedValues;
import net.bestia.model.geometry.Point;
import net.bestia.model.map.Walkspeed;
import net.bestia.zoneserver.battle.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
public class MovingService {

  private static final Logger LOG = LoggerFactory.getLogger(MovingService.class);

  private static final float TILES_PER_SECOND = 1.4f;
  private static final float SQRT_TWO = (float) Math.sqrt(2);

  private final EntityService entityService;
  private final StatusService statusService;
  private final EntitySearchService entitySearchService;

  @Autowired
  public MovingService(EntityService entityService,
                       StatusService statusService,
                       EntitySearchService entitySearchService) {

    this.entitySearchService = Objects.requireNonNull(entitySearchService);
    this.entityService = Objects.requireNonNull(entityService);
    this.statusService = Objects.requireNonNull(statusService);
  }

  /**
   * Calculates the next movement tick depending on the move speed. If -1 is
   * returned this means that the unit can no longer move (an error occurred
   * while calculating the movement) or the unit is considered to be static
   * and non movable.
   *
   * @param entity The entity which wants to move.
   * @param newPos The new position.
   * @return The delay in ms for the next movement tick, or -1 if an error has
   * occurred.
   */
  public int getMoveDelayMs(Entity entity, Point newPos) {

    final Walkspeed walkspeed = statusService.getStatusBasedValues(entity)
            .map(StatusBasedValues::getWalkspeedMod)
            .orElse(Walkspeed.ZERO);

    final Optional<Point> pos = entityService.getComponent(entity, PositionComponent.class)
            .map(PositionComponent::getPosition);

    if (walkspeed == Walkspeed.ZERO || !pos.isPresent()) {
      return -1;
    }

    final double d = pos.get().getDistance(newPos);
    float diagMult = 1;

    // Distance should be 1 or 2 (which means walking diagonally)
    if (d > 1) {
      diagMult *= SQRT_TWO;
    }

    return (int) Math.floor((1 / TILES_PER_SECOND) * 1000 * (1 / walkspeed.getSpeed()) * diagMult);
  }

  /**
   * Alias of {@link #getMoveDelayMs(Entity, Point)}. It just looks up the
   * entity before.
   *
   * @param entityId The ID of the entity to get the move delay.
   * @param newPos   The new position of the entity.
   * @return The delay in ms until this point is reached.
   */
  public int getMoveDelayMs(long entityId, Point newPos) {
    final Entity entity = entityService.getEntity(entityId);
    return getMoveDelayMs(entity, newPos);
  }

  /**
   * Sets the position of the given entity to a new point and performs all the
   * needed movement checks for triggering movement related effects. The moved
   * entity must have the {@link PositionComponent} otherwise it throws
   * {@link IllegalArgumentException}.
   *
   * @param entityId The entity to be moved.
   * @param newPos   The new position.
   */
  public void moveToPosition(long entityId, Point newPos) {

    // Before movement get all currently colliding entities.
    final Entity moveEntity = entityService.getEntity(entityId);
    final Set<Entity> preMoveCollisions = entitySearchService.getCollidingEntities(moveEntity);

    final PositionComponent posComp = entityService.getComponent(entityId, PositionComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    final Point oldPos = posComp.getPosition();

    // Move the entity to the new position.
    posComp.setPosition(newPos);

    final Direction newFacing = getDirection(oldPos, newPos, posComp.getFacing());
    posComp.setFacing(newFacing);

    final Set<Entity> postMoveCollisions = entitySearchService.getCollidingEntities(moveEntity);
    postMoveCollisions.removeAll(preMoveCollisions);

    LOG.trace("Moving entity {} to postition: {}. Facing: {}", entityId, newPos, newFacing);

    // TODO Check if a new collision has occurred and if necessary trigger
    // scripts.

    entityService.updateComponent(posComp);
  }

  /**
   * Calculates the direction of the entity which it is now facing. Usually an
   * entity faces the direction it moves. If old and new position are the
   * same, then the defaultDirection is returned.
   *
   * @param oldPos The old position.
   * @param newPos The new position.
   * @return The direction the unit now faces.
   */
  private Direction getDirection(Point oldPos, Point newPos, Direction defaultDirection) {

    if (oldPos.equals(newPos)) {
      return defaultDirection;
    }

    final long y = newPos.getY() - oldPos.getY();
    final long x = newPos.getX() - oldPos.getX();

    if (x == 0 && y > 0) {
      return Direction.SOUTH;
    } else if (x > 0 && y > 0) {
      return Direction.SOUTH_EAST;
    } else if (x > 0 && y == 0) {
      return Direction.EAST;
    } else if (x > 0 && y < 0) {
      return Direction.NORTH_EAST;
    } else if (x == 0 && y < 0) {
      return Direction.NORTH;
    } else if (x < 0 && y < 0) {
      return Direction.NORTH_WEST;
    } else if (x < 0 && y == 0) {
      return Direction.WEST;
    } else {
      return Direction.SOUTH_WEST;
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
  public void movePath(long entityId, List<Point> path) {
    LOG.trace("Moving entity {} along path: {}", entityId, path);
    final MoveComponent mc = entityService.getComponentOrCreate(entityId, MoveComponent.class);
    mc.setPath(path);
    entityService.updateComponent(mc);
  }
}
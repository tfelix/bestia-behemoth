package net.bestia.zoneserver.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorRef;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.entity.PeriodicMovementActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.components.PositionComponent;

/**
 * This manager holds references of currently moving entities and their movement
 * managing actors in order to control movement after it has been triggered. By
 * utilizing actor as the manager of the movement we need to check for possibly
 * triggered scripts/entities on the ways of this entity. Also there might be
 * generated input for AI entities.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class MovingEntityService {

	private static final Logger LOG = LoggerFactory.getLogger(MovingEntityService.class);

	private static final String MOVEMENT_KEY = "entity.moving";
	private final Map<Long, ActorRef> movingActorRefs;

	private final HazelcastInstance cache;
	private final EntityService entityService;

	@Autowired
	public MovingEntityService(HazelcastInstance cache, EntityService entityService) {

		this.cache = Objects.requireNonNull(cache);
		this.movingActorRefs = cache.getMap(MOVEMENT_KEY);
		this.entityService = Objects.requireNonNull(entityService);
	}

	private void setMovingActorRef(long entityId, ActorRef ref) {
		movingActorRefs.put(entityId, ref);
	}

	private void removeMovingActorRef(long entityId) {
		movingActorRefs.remove(entityId);
	}

	private ActorRef getMovingActorRef(long entityId) {
		return movingActorRefs.get(entityId);
	}

	public void movePath(long entityId, List<Point> path) {

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.
		ActorRef moveActor = getMovingActorRef(entityId);

		if (moveActor != null) {
			moveActor.tell(PeriodicMovementActor.STOP_MESSAGE, getSelf());
		} else {
			// Start a new movement via spawning a new movement tick actor with
			// the route to move and the movement speed determines the ticking
			// speed.
			moveActor = SpringExtension.createUnnamedActor(getContext(), TimedMoveActor.class);
		}

		// Tell the client the movement prediction message.
		final Entity entity = serviceCtx.getEntity().getEntity(moveMsg.getEntityId());

		if (entity == null) {
			return;
		}

		if (!serviceCtx.getEntity().hasComponent(entity, StatusComponent.class)) {
			return;
		}

		final StatusComponent status = serviceCtx.getEntity().getComponent(entity, StatusComponent.class).get();

		final EntityMoveMessage updateMsg = new EntityMoveMessage(
				moveMsg.getEntityId(),
				moveMsg.getPath(),
				status.getStatusBasedValues().getWalkspeed());
		sendActiveInRangeClients(updateMsg);

		moveActor.tell(msg, getSelf());
	}

	public void moveTo(long entityId, Point pos) {
		// not implemented.

		// Before movement get all currently colliding entities.
		final Entity moveEntity = entityService.getEntity(entityId);
		final Set<Entity> preMoveCollisions = entityService.getAllCollidingEntities(moveEntity);

		// Move the entity to the new position.

		final Set<Entity> postMoveCollisions = entityService.getAllCollidingEntities(moveEntity);
		postMoveCollisions.removeAll(preMoveCollisions);
		//postMoveCollisions.stream().filter(e -> entityService.hasComponent(e, PositionComponent)).filter(e -> entityService.hasComponent(e, clazz))
		
		
		// Check if a new collision has occurred and if necessary trigger
		// scripts.

		// Update all active players in sight with the current movement path.

		// Update all AI actors.
	}

	/**
	 * This is a very crucial method. It performs all needed checks of a entity
	 * when an movement has occurred. It will check if there are now collisions
	 * between
	 */
	private void doPostMoveCheck(Entity entity) {

	}
}

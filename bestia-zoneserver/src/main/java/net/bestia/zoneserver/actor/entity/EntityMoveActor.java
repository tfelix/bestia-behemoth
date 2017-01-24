package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.MovingEntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * Upon receiving of a move message we will lookup the movable entity and sets
 * them to the new position.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityMoveActor extends BestiaRoutingActor {

	public final static String NAME = "entityMove";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final MovingEntityService movingService;
	private final PlayerEntityService playerEntityService;

	@Autowired
	public EntityMoveActor(MovingEntityService movingService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(EntityMoveMessage.class, EntityMoveInternalMessage.class));

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	protected void handleMessage(Object msg) {

		if (msg instanceof EntityMoveInternalMessage) {
			handleInternalMove((EntityMoveInternalMessage) msg);
		} else {
			handleMove((EntityMoveMessage) msg);
		}
	}

	private void handleInternalMove(EntityMoveInternalMessage msg) {
		
		LOG.debug("Received internal move message: {}", msg.toString());

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.
		ActorRef moveActor = movingService.getMovingActorRef(msg.getEntityId());

		if (moveActor != null) {
			moveActor.tell(TimedMoveActor.STOP_MESSAGE, getSelf());
		} else {
			// Start a new movement via spawning a new movement tick actor with
			// the route to move and the movement speed determines the ticking
			// speed.
			moveActor = createUnnamedActor(TimedMoveActor.class);
		}

		moveActor.tell(msg, getSelf());
	}

	private void handleMove(EntityMoveMessage msg) {

		LOG.debug("Received player move message: {}", msg.toString());

		if (!playerEntityService.hasPlayerEntity(msg.getAccountId(), msg.getEntityId())) {
			LOG.warning("Player {} does not own entity {}.", msg.getAccountId(), msg.getEntityId());
			return;
		}

		// Transform to an internal message.
		handleInternalMove(new EntityMoveInternalMessage(msg.getEntityId(), msg.getPath()));
	}
}

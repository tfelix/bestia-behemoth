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
		super(Arrays.asList(EntityMoveMessage.class));

		this.playerEntityService = Objects.requireNonNull(playerEntityService);
		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	protected void handleMessage(Object msg) {

		// If we receive a bestia move message we must first convert it.
		final EntityMoveMessage moveMsg = (EntityMoveMessage) msg;

		LOG.debug("Received move message: {}", moveMsg.toString());

		if (!playerEntityService.hasPlayerEntity(moveMsg.getAccountId(), moveMsg.getEntityId())) {
			LOG.warning("Player {} does not own entity {}.", moveMsg.getAccountId(), moveMsg.getEntityId());
			return;
		}

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.
		ActorRef moveActor = movingService.getMovingActorRef(moveMsg.getEntityId());

		if (moveActor != null) {
			moveActor.tell(TimedMoveActor.STOP_MESSAGE, getSelf());
		} else {
			// Start a new movement via spawning a new movement tick actor with
			// the route to move and the movement speed determines the ticking
			// speed.
			moveActor = createUnnamedActor(TimedMoveActor.class);
		}

		moveActor.tell(moveMsg, getSelf());
	}
}

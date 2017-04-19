package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveInternalMessage;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.PlayerEntityService;
import net.bestia.zoneserver.service.MovingEntityService;

/**
 * Handle movement of an entity. It will announce the intended move path with
 * timing to all clients in sight so they can start to show the walk animation
 * and will perform the movement timer triggers so the unit does move from tile
 * to tile.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class MovementActor extends BestiaRoutingActor {

	public final static String NAME = "movement";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final MovingEntityService movingService;
	private final EntityService entityService;

	@Autowired
	public MovementActor(MovingEntityService movingService,
			PlayerEntityService playerEntityService,
			EntityService entityService) {
		super(Arrays.asList(EntityMoveInternalMessage.class));

		this.movingService = Objects.requireNonNull(movingService);
		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received internal move message: {}", msg.toString());

		final EntityMoveInternalMessage moveMsg = (EntityMoveInternalMessage) msg;

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.
		ActorRef moveActor = movingService.getMovingActorRef(moveMsg.getEntityId());

		if (moveActor != null) {
			moveActor.tell(TimedMoveActor.STOP_MESSAGE, getSelf());
		} else {
			// Start a new movement via spawning a new movement tick actor with
			// the route to move and the movement speed determines the ticking
			// speed.
			moveActor = SpringExtension.createUnnamedActor(getContext(), TimedMoveActor.class);
		}

		// Tell the client the movement prediction message.
		final Locatable entity = entityService.getEntity(moveMsg.getEntityId(), Locatable.class);

		if (entity == null) {
			return;
		}

		final EntityMoveMessage updateMsg = new EntityMoveMessage(
				moveMsg.getEntityId(),
				moveMsg.getPath(),
				entity.getMovementSpeed());
		sendActiveInRangeClients(updateMsg);

		moveActor.tell(msg, getSelf());
	}
}

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
import net.bestia.messages.internal.entity.ActiveUpateMessage;
import net.bestia.messages.internal.entity.EntityMoveInternalMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.MovingEntityService;
import net.bestia.zoneserver.service.PlayerEntityService;

/**
 * Handle movement of an entity originating from an entity itself. It will
 * announce the intended move path with timing to all clients in sight and will
 * perform the reoccuring movement timings.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityInternalMoveActor extends BestiaRoutingActor {

	public final static String NAME = "entityInternalMove";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final MovingEntityService movingService;

	@Autowired
	public EntityInternalMoveActor(MovingEntityService movingService, PlayerEntityService playerEntityService) {
		super(Arrays.asList(EntityMoveInternalMessage.class));

		this.movingService = Objects.requireNonNull(movingService);
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
			moveActor = createUnnamedActor(TimedMoveActor.class);
		}

		moveActor.tell(msg, getSelf());
		
		// Tell the client the movement prediction message.
		// TODO Speed von entity bestimmen.
		final float speed = 1.0f;
		final EntityMoveMessage updateMsg =  new EntityMoveMessage(moveMsg.getEntityId(), moveMsg.getPath(), speed);
		final ActiveUpateMessage wrappedUpdateMsg = ActiveUpateMessage.wrap(moveMsg.getEntityId(), updateMsg);
		sendActiveClients(wrappedUpdateMsg);
	}
}

package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.MovingEntityService;

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

	@Autowired
	public EntityMoveActor(MovingEntityService movingService) {
		super(Arrays.asList(EntityMoveMessage.class));

		this.movingService = Objects.requireNonNull(movingService);
	}

	@Override
	protected void handleMessage(Object msg) {
		
		// If we receive a bestia move message we must first convert it.
		final EntityMoveMessage moveMsg = (EntityMoveMessage) msg;
		
		LOG.debug("Received move message: {}", moveMsg.toString());

		// Check if the entity is already moving.
		// If this is the case cancel the current movement.
		ActorRef moveActor = movingService.getMovingActorRef(moveMsg.getEntityId());
		
		if(moveActor != null) {
			moveActor.tell(PoisonPill.getInstance(), getSelf());
		}

		// Then start a new movement via spawning a new movement tick actor with
		// the route to move and the movement speed determines the ticking
		// speed.
		moveActor = createActor(TimedMoveActor.class);
		moveActor.tell(moveMsg, getSelf());
	}
}

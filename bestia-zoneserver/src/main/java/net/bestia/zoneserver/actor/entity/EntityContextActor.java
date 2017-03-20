package net.bestia.zoneserver.actor.entity;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;

/**
 * This actor is responsible for receiving messages from the entities and
 * sending them to the appropriate sub actors which handle the messages. It is
 * the central entry point for sending messages from entities back to connected
 * users. But it is also used for entity to entity communication.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityContextActor extends BestiaRoutingActor {

	public static final String NAME = "entityContext";
	
	private final ActorRef activeClientUpdateRef;

	
	public EntityContextActor() {

		activeClientUpdateRef = createActor(ActiveClientUpdateActor.class);
		createActor(MovementActor.class);
		createActor(EntitySpawnActor.class);
		createActor(PositionActor.class);
		createActor(EntityDeleteActor.class);
	}

	@Override
	protected void handleMessage(Object msg) {
		// no op.
	}

	/**
	 * We have to handle incoming {@link EntityJsonMessage}. These messages are
	 * usually meant to be send to all active players in range. But since the
	 * way {@link BestiaRoutingActor} handles message filtering (no subtypes are
	 * supported, only the direct type), we need to filter our messages in this
	 * catch all method and redirect them to the right actor.
	 */
	@Override
	protected void handleUnknownMessage(Object msg) {
		
		if(msg instanceof EntityJsonMessage) {
			activeClientUpdateRef.tell(msg, getSelf());
		} else {
			super.handleUnknownMessage(msg);
		}
		
	}

}

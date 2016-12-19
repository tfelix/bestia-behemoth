package net.bestia.zoneserver.actor.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

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
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	@Autowired
	public EntityContextActor() {
		createActor(EntityUpdateActor.class);
	}

	@Override
	protected void handleMessage(Object msg) {
		// TODO Auto-generated method stub
		
	}

}

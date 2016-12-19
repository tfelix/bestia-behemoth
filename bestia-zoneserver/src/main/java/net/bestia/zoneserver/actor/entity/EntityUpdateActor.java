package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * This actor sends update messages regarding to entities to all active players
 * in range of the emitting entity.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class EntityUpdateActor extends BestiaRoutingActor {
	
	public final static String NAME = "entityUpdate";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	public EntityUpdateActor() {
		super(Arrays.asList(EntityPositionMessage.class));
		
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.info("Sending message to entities.");
	}

}

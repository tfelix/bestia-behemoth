package net.bestia.zoneserver.actor.entity;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;
import net.bestia.zoneserver.service.EntityService;

/**
 * This actor has an crucial role in checking if a position update leads to the
 * triggering of scripts or other things.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class PositionUpdateActor extends BestiaRoutingActor {

	public final static String NAME = "entityPositionUpdate";

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final EntityService entityService;

	@Autowired
	public PositionUpdateActor(EntityService entityService) {
		super(Arrays.asList(EntityPositionMessage.class));

		this.entityService = Objects.requireNonNull(entityService);
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug("Received internal position update.");
		
		final EntityPositionMessage posMsg = (EntityPositionMessage) msg;
		
		// TODO Perform the checks.
		
		// Update the client.
		sendActiveClients(posMsg);
	}

}

package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.PingMessage;
import net.bestia.messages.internal.PongMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

/**
 * Listens for ping messages and answers with a pong.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class PingPongActor extends BestiaRoutingActor {

	public final static String NAME = "pingpong";
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public PingPongActor() {
		super(Arrays.asList(PingMessage.class));
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));
		final PingMessage pmsg = (PingMessage) msg;
		
		sendClient(new PongMessage(pmsg));
	}

}

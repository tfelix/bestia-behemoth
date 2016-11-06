package net.bestia.zoneserver.actor.zone;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
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
	private final Set<Class<? extends Message>> HANDLED_CLASSES = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(PingMessage.class)));

	public PingPongActor() {

	}
	
	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_CLASSES;
	}

	@Override
	protected void handleMessage(Object msg) {
		LOG.debug(String.format("Received: %s", msg.toString()));
		final PingMessage pmsg = (PingMessage) msg;
		
		sendClient(new PongMessage(pmsg));
	}

}

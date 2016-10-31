package net.bestia.zoneserver.actor.test;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

@Component
@Scope("prototype")
public class RoutingRootTest extends BestiaRoutingActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	final ActorRef actor1;
	
	public RoutingRootTest() {
		
		actor1 = createActor(RoutingTest1.class, "test1");
	}

	@Override
	protected void handleMessage(Object msg) {
		// Fine to cast since.
		final ChatMessage chatMsg = (ChatMessage)msg;
		
		LOG.debug("HANDLED: " + chatMsg.toString());
	}


}

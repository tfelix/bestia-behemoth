package net.bestia.zoneserver.actor.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.Message;
import net.bestia.messages.chat.ChatMessage;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

@Component
@Scope("prototype")
public class RoutingTest1 extends BestiaRoutingActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final Set<Class<? extends Message>> HANDLED_MESSAGES = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList(ChatMessage.class)));

	@Override
	protected void handleMessage(Object msg) {
		// Fine to cast since.
		final ChatMessage chatMsg = (ChatMessage)msg;
		
		LOG.debug("HANDLED: " + chatMsg.toString());
	}

	@Override
	protected Set<Class<? extends Message>> getHandledMessages() {
		return HANDLED_MESSAGES;
	}

}

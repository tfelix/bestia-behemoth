package net.bestia.zoneserver.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;

/**
 * The {@link RestRouterActor} extracts messages from the wrapper message for
 * rest actors and then send the unwrapped message to the right actor.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class BRouterActor extends AbstractActor {

	/**
	 * This message lets the router actor redirect all incoming messages of this
	 * type to the requester of this message.
	 *
	 */
	public static final class RedirectMessage {

		private final Class<?> messageClass;
		private final ActorRef receiver;

		public RedirectMessage(Class<?> messageClass, ActorRef receiver) {

			this.messageClass = Objects.requireNonNull(messageClass);
			this.receiver = Objects.requireNonNull(receiver);
		}

		public Class<?> getMessageClass() {
			return messageClass;
		}

		public ActorRef getReceiver() {
			return receiver;
		}
	}

	private Map<Class<?>, ActorRef> redirectionActors = new HashMap<>();
	

	@Override
	public final Receive createReceive() {
		return receiveBuilder()
				.match(RedirectMessage.class, this::handleRequestMessage)
				.matchAny(this::handleIncomingMessage)
				.build();
	}

	/**
	 * Saves the message type if incoming messages match this type the message
	 * is redirected.
	 */
	private void handleRequestMessage(RedirectMessage msg) {
		redirectionActors.put(msg.getMessageClass(), msg.getReceiver());
	}
	
	private void handleIncomingMessage(Object msg) {
		if(redirectionActors.containsKey(msg.getClass())) {
			redirectionActors.get(msg.getClass()).forward(msg, getContext());
		} else {
			unhandled(msg);
		}
	}
}

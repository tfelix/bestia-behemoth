package net.bestia.zoneserver.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import net.bestia.messages.Message;

/**
 * The routing actor implementation will provide a method to add child actors.
 * These are asked which message they can handle via
 * {@link ReportHandledMessages}. If a message of this type is now received it
 * will get delivered.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public class BestiaRoutingActor extends UntypedActor {

	public static class ReportHandledMessages {
	};

	public static class HandledMessages {

		private final List<? extends Message> handledMessages = new ArrayList<>();

		public List<? extends Message> getHandledMessages() {
			return handledMessages;
		}
	}

	private Map<Class<? extends Message>, List<ActorRef>> messageRoutes = new HashMap<>();

	protected void addActor(ActorRef actor) {
		Objects.requireNonNull(actor);
		
		// Ask the actor which messages he can handle.
		actor.tell(new ReportHandledMessages(), getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof HandledMessages) {
			final HandledMessages msg = (HandledMessages) message;

			// Add this actor ref to our routes.
			msg.getHandledMessages().forEach(x -> {

				if (!messageRoutes.containsKey(x)) {
					messageRoutes.put(x.getClass(), new ArrayList<>());
				}

				messageRoutes.get(x.getClass()).add(getSender());
			});

		} else {
			// Check if one of our routes can handle the message.
			final List<ActorRef> refs = messageRoutes.get(message.getClass());

			if (refs == null) {
				unhandled(message);
				return;
			}

			refs.forEach(x -> x.tell(message, getSender()));
		}

	}

}

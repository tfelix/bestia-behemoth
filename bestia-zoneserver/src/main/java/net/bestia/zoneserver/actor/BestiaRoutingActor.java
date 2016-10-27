package net.bestia.zoneserver.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import net.bestia.messages.Message;
import net.bestia.messages.MessageId;
import net.bestia.zoneserver.actor.SpringExtension.SpringExt;

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
public abstract class BestiaRoutingActor extends UntypedActor {

	public static class ReportHandledMessages {
	};

	public static class HandledMessages {

		private List<Class<? extends Message>> handledMessages = new ArrayList<>();

		/**
		 * 
		 * @param handledMessages
		 */
		public HandledMessages(List<Class<? extends Message>> handledMessages) {
			Objects.requireNonNull(handledMessages);
			this.handledMessages.addAll(handledMessages);
		}

		public List<Class<? extends Message>> getHandledMessages() {
			return handledMessages;
		}
	}

	private Map<Class<? extends Message>, List<ActorRef>> messageRoutes = new HashMap<>();

	/**
	 * Creates a new actor and alread register it with this routing actor so it
	 * is considered when receiving messages.
	 * 
	 * @param clazz
	 *            The class of the {@link UntypedActor} to instantiate.
	 * @param name
	 *            The name under which the actor should be created.
	 * @return The created and already registered new actor.
	 */
	protected ActorRef createAndRegisterActor(Class<? extends UntypedActor> clazz, String name) {
		final SpringExt springExt = SpringExtension.Provider.get(getContext().system());
		final Props props = springExt.props(clazz);
		final ActorRef newActor = getContext().actorOf(props, name);
		addActor(newActor);

		return newActor;
	}

	/**
	 * Must be called if a actor is created inside this actor. This method will
	 * ask this actor which messages he can handle and save the router for later
	 * processing.
	 * 
	 * @param actor
	 */
	protected void addActor(ActorRef actor) {
		Objects.requireNonNull(actor);

		// Ask the actor which messages he can handle.
		actor.tell(new ReportHandledMessages(), getSelf());
	}

	/**
	 * Returns a list of handled message ids by this actor. They will get routed
	 * via its parent actor.
	 * 
	 * @return A list of message IDs handled by this actor.
	 */
	protected abstract List<Class<? extends Message>> getHandledMessages();

	/**
	 * If a message belongs to ourself
	 * 
	 * @param msg
	 */
	protected abstract void handleMessage(MessageId msg);

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof ReportHandledMessages) {
			// Report back with all me messages we can handle. This means the
			// message THIS actor can handle, as well as all the messages the
			// child actor can handle.
			final List<Class<? extends Message>> ownHandledMsgs = getHandledMessages();
			ownHandledMsgs.addAll(messageRoutes.keySet());
			getSender().tell(new HandledMessages(ownHandledMsgs), getSelf());

		} else if (message instanceof HandledMessages) {
			final HandledMessages msg = (HandledMessages) message;

			// Add this actor ref to our routes.
			msg.getHandledMessages().forEach(x -> {

				if (!messageRoutes.containsKey(x)) {
					messageRoutes.put(x, new ArrayList<>());
				}

				messageRoutes.get(x).add(getSender());
			});
		} else {
			// Check if one of our routes can handle the message.
			boolean wasHandled = false;

			if (messageRoutes.containsKey(message.getClass())) {
				wasHandled = true;
				final List<ActorRef> refs = messageRoutes.get(message.getClass());
				refs.forEach(x -> x.tell(message, getSender()));
			}

			// Check if WE can handle this message by ourself.
			if (getHandledMessages().contains(message.getClass())) {
				wasHandled = true;
				handleMessage((MessageId) message);
			}

			if (!wasHandled) {
				unhandled(message);
			}
		}
	}
}

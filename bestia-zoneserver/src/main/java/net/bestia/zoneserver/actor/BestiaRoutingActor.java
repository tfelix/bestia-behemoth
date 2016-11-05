package net.bestia.zoneserver.actor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JacksonMessage;
import net.bestia.messages.Message;
import net.bestia.messages.internal.ReportHandledMessages;
import net.bestia.zoneserver.actor.zone.SendClientActor;

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
public abstract class BestiaRoutingActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private Map<Class<? extends Message>, Set<ActorRef>> messageRoutes = new HashMap<>();
	private ActorRef responder;

	/**
	 * Returns a list of message classes which are handled by this actor. They
	 * will get routed via its parent actor. Via default the
	 * {@link BestiaRoutingActor} does not handle any messages on its own.
	 * 
	 * @return A unmodifiable set of message classes handled by this actor.
	 */
	private Set<Class<? extends Message>> getAllHandledMessages() {
		final Set<Class<? extends Message>> mergedSet = new HashSet<>();
		mergedSet.addAll(getHandledMessages());
		mergedSet.addAll(messageRoutes.keySet());
		return Collections.unmodifiableSet(mergedSet);
	}

	/**
	 * Implementing classes must (or can) overwrite this method in order to
	 * announce own handled messages. These messages will be joined with the
	 * rest of the messages this actor and all of its child are handling.
	 * 
	 * @return
	 */
	protected Set<Class<? extends Message>> getHandledMessages() {
		return Collections.emptySet();
	}

	/**
	 * If a message is handled by this implementation of the routing actor the
	 * method is getting called.
	 * 
	 * @param msg
	 */
	protected abstract void handleMessage(Object msg);

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 */
	protected void sendClient(JacksonMessage msg) {
		LOG.debug(String.format("Sending to client: %s", msg.toString()));
		if(responder == null) {
			responder = createActor(SendClientActor.class);
		}

		responder.tell(msg, getSelf());
	}

	/**
	 * Reports to the parent which messages are handled by us.
	 */
	@Override
	public void preStart() throws Exception {
		final ReportHandledMessages msg = new ReportHandledMessages(getHandledMessages());
		getContext().parent().tell(msg, getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		boolean handled = false;

		if (message instanceof ReportHandledMessages) {
			addHandledRoutes((ReportHandledMessages) message);
			return;
		}

		if (getHandledMessages().contains(message.getClass())) {
			handleMessage(message);
			handled = true;
		}

		// Check if we have child actor handling the incoming message.
		if (messageRoutes.containsKey(message.getClass())) {
			messageRoutes.get(message.getClass()).forEach(x -> x.tell(message, getSender()));
			handled = true;
		}

		if (!handled) {
			LOG.warning("Zone received unknown message: {}", message);
			unhandled(message);
		}
	}

	/**
	 * Adds the incoming message routes to the system.
	 */
	private void addHandledRoutes(ReportHandledMessages message) {
		message.getHandledMessages().forEach(x -> {
			if (!messageRoutes.containsKey(x)) {
				messageRoutes.put(x, new HashSet<>());
			}
			messageRoutes.get(x).add(getSender());
		});

		// Announce the changed routing table upstream.
		final ReportHandledMessages reportMsg = new ReportHandledMessages(getAllHandledMessages());
		getContext().parent().tell(reportMsg, getSelf());
	}

	@Override
	public String toString() {
		return String.format("BestiaRoutingActor[handles: {%s}]", getHandledMessages().toString());
	}
}

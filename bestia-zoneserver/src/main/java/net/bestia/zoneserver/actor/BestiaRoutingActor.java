package net.bestia.zoneserver.actor;

import java.util.Collection;
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

	private final Set<Class<? extends Object>> ownHandler;
	private final Map<Class<? extends Object>, Set<ActorRef>> childHandler = new HashMap<>();
	private ActorRef responder;

	public BestiaRoutingActor() {
		ownHandler = Collections.unmodifiableSet(new HashSet<>());
	}

	public BestiaRoutingActor(Collection<Class<? extends Object>> handledMessages) {
		ownHandler = Collections.unmodifiableSet(new HashSet<>(handledMessages));
	}

	public BestiaRoutingActor(Collection<Class<? extends Object>> handledMessages, boolean announceRoute) {
		ownHandler = Collections.unmodifiableSet(new HashSet<>(handledMessages));
	}

	/**
	 * Returns a list of message classes which are handled by this actor. They
	 * will get routed via its parent actor. Via default the
	 * {@link BestiaRoutingActor} does not handle any messages on its own.
	 * 
	 * @return A unmodifiable set of message classes handled by this actor.
	 */
	private Set<Class<? extends Object>> getAllHandledMessages() {
		final Set<Class<? extends Object>> mergedSet = new HashSet<>();
		mergedSet.addAll(ownHandler);
		mergedSet.addAll(childHandler.keySet());
		return Collections.unmodifiableSet(mergedSet);
	}

	/**
	 * If a message is handled by this implementation of the routing actor the
	 * method is getting called.
	 * 
	 * @param msg
	 * @return Flag if the message was consumed and successfully handled.
	 *         Further message propagation is stopped.
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
		if (responder == null) {
			responder = createActor(SendClientActor.class);
		}

		responder.tell(msg, getSelf());
	}

	/**
	 * Reports to the parent which messages are handled by us.
	 */
	@Override
	public void preStart() throws Exception {
		final ReportHandledMessages msg = new ReportHandledMessages(ownHandler);
		getContext().parent().tell(msg, getSelf());
	}

	@Override
	public void onReceive(Object message) throws Exception {


		// Internal status messages must be used to handle the routing.
		if (message instanceof ReportHandledMessages) {
			addHandledRoutes((ReportHandledMessages) message);
			return;
		}

		if (ownHandler.contains(message.getClass())) {
			handleMessage(message);
			return;
		}

		// Check if we have child actor handling the incoming message.
		if (childHandler.containsKey(message.getClass())) {
			childHandler.get(message.getClass()).forEach(x -> x.tell(message, getSender()));
			return;
		}

		// Catch all remaining messages.
		handleUnknownMessage(message);
	}

	/**
	 * This method is called when there is no known message handler. Can be
	 * overwritten to do a custom, non normal behaviour.
	 * 
	 * @param msg
	 *            The unhandled message.
	 */
	protected void handleUnknownMessage(Object msg) {
		LOG.warning("Actor {} received unknown message: {}", getSelf().path().toString(), msg);
		unhandled(msg);
	}

	/**
	 * Adds the incoming message routes to the system.
	 */
	private void addHandledRoutes(ReportHandledMessages message) {
		if (message.getHandledMessages().isEmpty()) {
			return;
		}

		message.getHandledMessages().forEach(x -> {
			if (!childHandler.containsKey(x)) {
				childHandler.put(x, new HashSet<>());
			}
			childHandler.get(x).add(getSender());
		});

		// Announce the changed routing table upstream (only if we are not the
		// sender).
		if (!getContext().parent().equals(getSelf())) {
			final ReportHandledMessages reportMsg = new ReportHandledMessages(getAllHandledMessages());
			getContext().parent().tell(reportMsg, getSelf());
		}
	}

	@Override
	public String toString() {
		return String.format("BestiaRoutingActor[handles: {%s}]", ownHandler.toString());
	}
}

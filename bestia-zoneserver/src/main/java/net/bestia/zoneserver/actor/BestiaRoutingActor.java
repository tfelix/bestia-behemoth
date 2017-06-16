package net.bestia.zoneserver.actor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ReportHandledMessages;

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

	private boolean doChildRouting = true;
	private boolean hasReported = false;

	public BestiaRoutingActor() {
		ownHandler = Collections.unmodifiableSet(new HashSet<>());
	}

	public BestiaRoutingActor(Collection<Class<? extends Object>> handledMessages) {
		ownHandler = Collections.unmodifiableSet(new HashSet<>(handledMessages));
	}

	/**
	 * Flag controls if the {@link BestiaRoutingActor} will propagate the
	 * routing information of its children upstream. If this flag is set to
	 * false then no child {@link BestiaRoutingActor} will be reported upstream.
	 * 
	 * @param flag
	 */
	protected void setChildRouting(boolean flag) {
		doChildRouting = flag;

		// If we had already reported, we must re-report.
		if (hasReported) {
			if (doChildRouting) {
				// Re-send the handled child messages.
				final ReportHandledMessages reportMsg = new ReportHandledMessages(getAllHandledMessages());
				getContext().parent().tell(reportMsg, getSelf());
			} else {
				// Re-send the empty child message handling.
				final ReportHandledMessages reportMsg = new ReportHandledMessages();
				getContext().parent().tell(reportMsg, getSelf());
			}
		}
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
	 * Reports to the parent which messages are handled by us.
	 */
	@Override
	public void preStart() throws Exception {
		final ReportHandledMessages msg = new ReportHandledMessages(ownHandler);
		final ActorRef parent = getContext().parent();
		parent.tell(msg, getSelf());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ReportHandledMessages.class, this::setHandledRoutes)
				.matchAny(this::receive)
				.build();
	}

	public void receive(Object message) throws Exception {

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
	 * Sets the incoming message routes to the system. It will set the handled
	 * message to the handled messages of the childs.
	 */
	private void setHandledRoutes(ReportHandledMessages message) {

		removeHandledRoutes(getSender());

		message.getHandledMessages().forEach(x -> {

			if (!childHandler.containsKey(x)) {
				childHandler.put(x, new HashSet<>());
			}

			childHandler.get(x).add(getSender());
		});

		LOG.debug("Installed message routing: {}", childHandler.toString());

		// Announce the changed routing table upstream.
		// (only if we are not the sender).
		if (!getContext().parent().equals(getSelf()) && doChildRouting) {
			hasReported = true;
			final ReportHandledMessages reportMsg = new ReportHandledMessages(getAllHandledMessages());
			getContext().parent().tell(reportMsg, getSelf());
		}
	}

	/**
	 * Removes all routes to this sender.
	 * 
	 * @param sender
	 */
	private void removeHandledRoutes(ActorRef sender) {

		Iterator<Map.Entry<Class<? extends Object>, Set<ActorRef>>> iter = childHandler.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Class<? extends Object>, Set<ActorRef>> entry = iter.next();
			entry.getValue().remove(sender);

			// Remove whole set.
			if (entry.getValue().size() == 0) {
				iter.remove();
			}

		}
	}

	@Override
	public String toString() {
		return String.format("BestiaRoutingActor[handles: {%s}]", ownHandler.toString());
	}
}

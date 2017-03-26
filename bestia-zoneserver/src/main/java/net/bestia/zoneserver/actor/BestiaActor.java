package net.bestia.zoneserver.actor;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.zoneserver.actor.zone.ActiveClientUpdateActor;
import net.bestia.zoneserver.actor.zone.SendClientActor;

/**
 * Should be the base class for the whole akka system. This class provides some
 * helper methods to simply create dependency injected actors via spring.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Component
@Scope("prototype")
public abstract class BestiaActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private ActorRef responder;
	private ActorRef activeClientBroadcaster;

	public BestiaActor() {
		super();
	}

	/**
	 * This will deliver the given message back to the account. In order to do
	 * this a {@link SendClientActor} responder is used. The actor will be
	 * created when necessary (this means the method is first invoked).
	 * 
	 * @param msg
	 */
	protected void sendClient(JsonMessage msg) {
		LOG.debug(String.format("Sending to client %d: %s", msg.getAccountId(), msg.toString()));
		if (responder == null) {
			responder = SpringExtension.actorOf(getContext(), SendClientActor.class);
		}

		responder.tell(msg, getSelf());
	}

	/**
	 * Sends the given message back to all active player clients in sight. To to
	 * this an on demand {@link ActiveClientUpdateActor} is created.
	 * 
	 * @param msg
	 *            The update message to be send to all active clients in sight
	 *            of the referenced entity.
	 */
	protected void sendActiveInRangeClients(EntityJsonMessage msg) {
		if (activeClientBroadcaster == null) {
			activeClientBroadcaster = SpringExtension.actorOf(getContext(), ActiveClientUpdateActor.class);
		}

		activeClientBroadcaster.tell(msg, getSelf());
	}
}
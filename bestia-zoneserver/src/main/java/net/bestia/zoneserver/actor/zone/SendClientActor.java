package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorPath;
import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.zoneserver.service.ConnectionService;

/**
 * The {@link SendClientActor} is responsible for the delivery of messages
 * directed to clients. It will lookup the information (actor ref) of the the
 * account given in the account message, serialize the message and send it back
 * to the webserver the client is currently connected.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class SendClientActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClient";

	private final ConnectionService connectionService;

	@Autowired
	public SendClientActor(ConnectionService connectionService) {

		this.connectionService = Objects.requireNonNull(connectionService);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(AccountMessage.class, this::sendToClient)
				.build();
	}

	private void sendToClient(AccountMessage msg) {

		final ActorPath originPath = connectionService.getPath(msg.getAccountId());

		// Origin path might be null because of race conditions or because the
		// actor was logged out.
		if (originPath == null) {
			LOG.debug("Webserver path was null. Message not delivered: {}", msg);
			return;
		}

		final ActorSelection origin = context().actorSelection(originPath);

		if (origin == null) {
			LOG.warning("Could not find origin ref for message: {}", msg.toString());
			unhandled(msg);
			return;
		}

		// Send the client message.
		LOG.debug(String.format("Sending to client %d: %s", msg.getAccountId(), msg));
		origin.tell(msg, getSelf());

	}

}

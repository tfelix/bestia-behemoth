package net.bestia.zoneserver.actor.zone;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.ActorPath;
import akka.actor.ActorSelection;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.zoneserver.actor.BestiaActor;
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
public class SendClientActor extends BestiaActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	public static final String NAME = "sendToClient";
	
	private final ConnectionService connectionService;

	@Autowired
	public SendClientActor(ConnectionService connectionService) {

		this.connectionService = Objects.requireNonNull(connectionService);
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof AccountMessage) {

			final AccountMessage accMsg = (AccountMessage) message;
			final ActorPath originPath = connectionService.getPath(accMsg.getAccountId());
			final ActorSelection origin = getContext().actorSelection(originPath);

			if (origin == null) {
				LOG.warning("Could not find origin ref for message: {}", message.toString());
				unhandled(message);
				return;
			}

			// Send the client message.
			LOG.debug(String.format("Sending to client %d: %s", accMsg.getAccountId(), accMsg));
			origin.tell(message, getSelf());
		} else {
			// We handle only account messages.
			unhandled(message);
		}

	}

}

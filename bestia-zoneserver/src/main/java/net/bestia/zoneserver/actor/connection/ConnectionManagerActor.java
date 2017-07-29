package net.bestia.zoneserver.actor.connection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.zoneserver.actor.SpringExtension;

/**
 * This is a central manager actor which will start up sharded actors for each
 * connection. It manages the connection to the players and hold references to
 * the webserver to which they are connected.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ConnectionManagerActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "connection";

	public ConnectionManagerActor() {

	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Long.class, this::startConnectionActor)
				.build();
	}

	private void startConnectionActor(Long accountId) {
		final String actorName = ConnectionActor.getActorName(accountId);

		LOG.debug("Received start request for account connection: {}. Actor name: {}", accountId, actorName);

		SpringExtension.actorOf(getContext(), ConnectionActor.class, actorName, accountId);
	}
}

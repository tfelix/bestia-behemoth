package net.bestia.zoneserver.actor.connection;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.InvalidActorNameException;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;

/**
 * This is a central manager actor which will start up sharded actors for each
 * connection. It manages the connection to the players and hold references to
 * the webserver to which they are connected.
 * 
 * FIXME This must be done sharded.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ConnectionManagerActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	public static final String NAME = "connection";

	private Map<Long, ActorRef> idToConnections = new HashMap<>();
	private Map<ActorRef, Long> connectionsToId = new HashMap<>();

	public ConnectionManagerActor() {
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ClientConnectionStatusMessage.class, this::checkConnectionStatus)
				.match(Terminated.class, this::handleConnectionTeminated)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// After the start we must inform the ingest actor that we want to
		// receive messages.
		final RedirectMessage msg = RedirectMessage.get(ClientConnectionStatusMessage.class);
		context().parent().tell(msg, getSelf());
	}

	private void handleConnectionTeminated(Terminated t) {
		if(!connectionsToId.containsKey(t.actor())) {
			return;
		}
		
		final long connectionId = connectionsToId.get(t.actor());
		connectionsToId.remove(t.actor());
		idToConnections.remove(connectionId);
	}

	/**
	 * Checks if the user is connected or disconnected. Based upon this state a
	 * connection is established or closed.
	 * 
	 * @param msg
	 */
	private void checkConnectionStatus(ClientConnectionStatusMessage msg) {

		if (msg.getState() == ConnectionState.CONNECTED) {
			startConnectionActor(msg);
		} else {
			// Simplay forward message.
			sendToConnectionActor(msg.getAccountId(), msg);
		}
	}

	private void startConnectionActor(ClientConnectionStatusMessage msg) {
		final String actorName = ClientConnectionActor.getActorName(msg.getAccountId());
		try {
			final ActorRef connectionActor = SpringExtension.actorOf(
					getContext(),
					ClientConnectionActor.class,
					actorName,
					msg.getAccountId(), msg.getWebserverRef());
			
			getContext().watch(connectionActor);
			
			// Add it to the cache.
			connectionsToId.put(connectionActor, msg.getAccountId());
			idToConnections.put(msg.getAccountId(), connectionActor);

			LOG.debug("Received start request for account connection: {}. Actor name: {}",
					msg.getAccountId(),
					connectionActor.path().toString());

		} catch (InvalidActorNameException ex) {
			LOG.debug("Actor with actor name already active: {}", actorName);
			return;
		}
	}

	/**
	 * The message is forwarded towards the connection actor.
	 * 
	 * @param msg
	 */
	private void sendToConnectionActor(long accoundId, Object msg) {
		idToConnections.get(accoundId).tell(msg, getSelf());
	}
}

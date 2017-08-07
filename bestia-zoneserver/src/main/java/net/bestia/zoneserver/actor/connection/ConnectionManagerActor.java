package net.bestia.zoneserver.actor.connection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor.RedirectMessage;

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
		// no op.
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ClientConnectionStatusMessage.class, this::checkConnectionStatus)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// After the start we must inform the ingest actor that we want to
		// receive messages.
		final RedirectMessage msg = RedirectMessage.get(ClientConnectionStatusMessage.class);
		context().parent().tell(msg, getSelf());
	}

	/**
	 * Checks if the user is connected or disconnected. Based upon this state a
	 * connection is established or closed.
	 * 
	 * @param msg
	 */
	private void checkConnectionStatus(ClientConnectionStatusMessage msg) {

		if (msg.getState() == ConnectionState.CONNECTED) {
			// Start the connection actor.
			SpringExtension.actorOf(getContext(), 
					ConnectionActor.class,
					ConnectionActor.getActorName(msg.getAccountId()), 
					msg.getAccountId(), msg.getWebserverRef());
			
			final String actorName = ConnectionActor.getActorName(msg.getAccountId());
			final ActorRef connectionActor = SpringExtension.actorOf(getContext(), ConnectionActor.class, actorName,
					msg.getAccountId());

			LOG.debug("Received start request for account connection: {}. Actor name: {}",
					msg.getAccountId(),
					connectionActor.path().toString());
			
		} else {
			// Terminate if there is a connection existing.
			sendToConnectionActor(msg.getAccountId(), PoisonPill.getInstance());
		}

	}

	/**
	 * The message is forwarded towards the connection actor.
	 * 
	 * @param msg
	 */
	private void sendToConnectionActor(long accoundId, Object msg) {
		final String connectionActorName = ConnectionActor.getActorName(accoundId);
		final String actorName = AkkaCluster.getNodeName(NAME, connectionActorName);
		getContext().actorSelection(actorName).tell(msg, getSelf());
	}
}

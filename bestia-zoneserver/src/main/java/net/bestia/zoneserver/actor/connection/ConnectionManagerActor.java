package net.bestia.zoneserver.actor.connection;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.misc.PongMessage;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.AkkaSender;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.actor.zone.IngestExActor;
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
				.match(Long.class, this::startConnectionActor)
				.match(ClientConnectionStatusMessage.class, this::redirectToConnectionActor)
				.match(PongMessage.class, this::redirectToConnectionActor)
				.build();
	}
	
	@Override
	public void preStart() throws Exception {
		// After the start we must inform the ingest actor that we want to
		// receive messages.
		final RedirectMessage msg = RedirectMessage.get(PongMessage.class, ClientConnectionStatusMessage.class);
		AkkaSender.sendToActor(getContext(), IngestExActor.NAME, msg, getSelf());
	}

	/**
	 * This messages are usually send if there was a login detected. The message
	 * is forwarded to the connection actor.
	 * 
	 * @param msg
	 *            The message to redirect to the connection actor.
	 */
	private void redirectToConnectionActor(AccountMessage msg) {
		final String connectionActorName = ConnectionActor.getActorName(msg.getAccountId());
		final String actorName = AkkaCluster.getNodeName(NAME, connectionActorName);
		getContext().actorSelection(actorName).tell(msg, getSelf());
	}

	private void startConnectionActor(Long accountId) {
		final String actorName = ConnectionActor.getActorName(accountId);
		final ActorRef connectionActor = SpringExtension.actorOf(getContext(), ConnectionActor.class, actorName,
				accountId);

		LOG.debug("Received start request for account connection: {}. Actor name: {}",
				accountId,
				connectionActor.path().toString());
	}
}

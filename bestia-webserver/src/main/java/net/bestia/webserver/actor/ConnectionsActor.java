package net.bestia.webserver.actor;

import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.messages.web.ClientPayloadMessage;
import net.bestia.webserver.messages.web.CloseConnection;
import net.bestia.webserver.messages.web.ZoneConnectionAccepted;

/**
 * Holds a reference to all currently conneceted client sockets.
 * 
 * @author Thomas Felix
 *
 */
public class ConnectionsActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorRef uplink;
	private final ObjectMapper mapper = new ObjectMapper();

	private final BiMap<String, ActorRef> connections = HashBiMap.create();

	private ConnectionsActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
	}

	public static Props props(ActorRef uplink) {
		return Props.create(new Creator<ConnectionsActor>() {
			private static final long serialVersionUID = 1L;

			public ConnectionsActor create() throws Exception {
				return new ConnectionsActor(uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ZoneConnectionAccepted.class, this::handleNewConnection)
				.match(ClientPayloadMessage.class, this::handleClientPayloadMessage)
				.match(CloseConnection.class, this::handleClientSocketClosed)
				.match(Terminated.class, this::handleClosedConnection)
				.build();
	}

	/**
	 * Received if a message from a client is received.
	 * 
	 * @param msg
	 */
	private void handleClientPayloadMessage(ClientPayloadMessage msg) {

		final ActorRef socket = connections.get(msg.getSessionId());

		if (socket == null) {
			LOG.debug("No active connection for session: {}", msg.getSessionId());
			return;
		}

		socket.tell(msg.getMessage(), getSelf());
	}

	private void handleClientSocketClosed(CloseConnection msg) {

		final ActorRef connectionActor = connections.get(msg.getSessionId());
		if (connectionActor != null) {
			connectionActor.tell(PoisonPill.getInstance(), getSelf());
		}

	}

	private void handleClosedConnection(Terminated msg) {

		LOG.debug("Killing connection actor: {}", msg.actor());
		connections.inverse().remove(msg.actor());

	}

	/**
	 * Opens a new connection.
	 * 
	 * @param msg
	 */
	private void handleNewConnection(ZoneConnectionAccepted msg) {

		final String actorName = String.format("socket-auth-%s", msg.getSessionId());

		final Props socketProps = ClientActor.props(msg, mapper, uplink);
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);

		getContext().watch(socketActor);

		connections.put(msg.getSessionId(), socketActor);
		LOG.debug("Startet new connection actor: {}", socketActor);
	}

}

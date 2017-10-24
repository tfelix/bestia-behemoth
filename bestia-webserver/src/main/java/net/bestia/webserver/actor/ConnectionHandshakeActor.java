package net.bestia.webserver.actor;

import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.messages.web.PrepareConnection;
import net.bestia.webserver.messages.web.SocketMessage;
import net.bestia.webserver.messages.web.ZoneConnectionAccepted;

public class ConnectionHandshakeActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorRef uplink;
	private final ObjectMapper mapper = new ObjectMapper();
	private final BiMap<String, ActorRef> pendingConnections = HashBiMap.create();
	private final ActorRef connectionsActor;

	private ConnectionHandshakeActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
		this.connectionsActor = getContext().actorOf(ConnectionsActor.props(uplink), "openedConnections");
	}

	public static Props props(ActorRef uplink) {
		return Props.create(new Creator<ConnectionHandshakeActor>() {
			private static final long serialVersionUID = 1L;

			public ConnectionHandshakeActor create() throws Exception {
				return new ConnectionHandshakeActor(uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(PrepareConnection.class, this::handlePrepareConnection)
				.match(ZoneConnectionAccepted.class, this::handleAcceptedConnection)
				.match(SocketMessage.class, this::redirectMessage)
				.match(Terminated.class, this::handleClosedConnection)
				.build();
	}
	
	private void redirectMessage(SocketMessage msg) {
		
		if (pendingConnections.containsKey(msg.getSessionId())) {
			pendingConnections.get(msg.getSessionId()).tell(msg, getSelf());
		} else {
			connectionsActor.tell(msg, getSelf());
		}
	}

	private void handleClosedConnection(Terminated msg) {

		// Check if this is an auth actor.
		LOG.debug("Killing connection actor: {}", msg.actor());
		pendingConnections.inverse().remove(msg.actor());

	}

	private void handlePrepareConnection(PrepareConnection msg) {

		// Depending from which the message originated we will interpret this
		// message as a start authentication or open the connection.
		if (pendingConnections.inverse().containsKey(getSender())) {
			// Message is from a pending connection. We will forward message to
			// open the connection.
			connectionsActor.tell(msg, getSelf());
			pendingConnections.inverse().remove(getSender());
		} else {
			final String actorName = String.format("socket-auth-%s", msg.getSessionId());

			final Props socketProps = ClientAuthActor.props(msg.getSessionId(), msg.getSession(), mapper, uplink);
			final ActorRef socketActor = getContext().actorOf(socketProps, actorName);
			
			getContext().watch(socketActor);

			pendingConnections.put(msg.getSessionId(), socketActor);
		}
	}

	/**
	 * Server accepted connection. Forward to open the connection.
	 * 
	 * @param msg
	 */
	private void handleAcceptedConnection(ZoneConnectionAccepted msg) {
		connectionsActor.tell(msg, getSelf());
	}
}

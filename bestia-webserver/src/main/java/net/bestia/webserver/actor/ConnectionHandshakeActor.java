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
	private final BiMap<String, ActorRef> connections = HashBiMap.create();

	private ConnectionHandshakeActor(ActorRef uplink) {

		this.uplink = Objects.requireNonNull(uplink);
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
		LOG.debug("Received from client: {}", msg);
		final ActorRef clientActor = connections.getOrDefault(msg.getSessionId(), ActorRef.noSender());
		clientActor.tell(msg, getSelf());
	}

	private void handleClosedConnection(Terminated msg) {

		// Check if this is an auth actor.
		LOG.debug("Killing connection actor: {}", msg.actor());
		connections.inverse().remove(msg.actor());

	}

	private void handlePrepareConnection(PrepareConnection msg) {

		final String actorName = String.format("socket-auth-%s", msg.getSessionId());
		final Props socketProps = ClientAuthActor.props(msg.getSessionId(), msg.getSession(), mapper, uplink);
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);

		getContext().watch(socketActor);

		connections.put(msg.getSessionId(), socketActor);
	}

	/**
	 * Server accepted connection. Forward to open the connection.
	 * 
	 * @param msg
	 */
	private void handleAcceptedConnection(ZoneConnectionAccepted msg) {
		final String actorName = String.format("socket-%s", msg.getSessionId());
		final Props socketProps = ClientActor.props(uplink, mapper, msg.getSession());
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);

		getContext().watch(socketActor);

		connections.put(msg.getSessionId(), socketActor);
		socketActor.tell(msg, getSelf());
	}
}

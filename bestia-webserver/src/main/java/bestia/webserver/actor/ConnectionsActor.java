package bestia.webserver.actor;

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
import bestia.webserver.messages.web.ClientPayloadMessage;
import bestia.webserver.messages.web.CloseConnection;
import bestia.webserver.messages.web.OpenConnection;

/**
 * Holds a reference to all currently connected client sockets and manages them.
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
				.match(OpenConnection.class, this::handleClientSocketOpened)
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

		final ActorRef socketActor = connections.get(msg.getSessionId());

		if (socketActor == null) {
			LOG.debug("No active connection for session: {}", msg.getSessionId());
			return;
		}

		socketActor.tell(msg, getSelf());
	}
	
	private void handleClientSocketOpened(OpenConnection msg) {
		
		final Props socketProps = ClientActor.props(uplink, mapper, msg.getSession());
		final String actorName = String.format("socket-%s", msg.getSessionId());
		final ActorRef socketActor = getContext().actorOf(socketProps, actorName);
		
		getContext().watch(socketActor);
		connections.put(msg.getSessionId(), socketActor);
		
		LOG.debug("Client {} opened connection. Starting actor {}.", msg.getSessionId(), socketActor);
		
	}

	private void handleClientSocketClosed(CloseConnection msg) {
		
		LOG.debug("Client {} closed connection. Stopping actor.", msg.getSessionId());

		final ActorRef connectionActor = connections.get(msg.getSessionId());
		if (connectionActor != null) {
			connectionActor.tell(PoisonPill.getInstance(), getSelf());
		}
	}

	private void handleClosedConnection(Terminated msg) {

		LOG.debug("Removing closed connection actor: {}", msg.actor());
		
		connections.inverse().remove(msg.actor());

	}
}

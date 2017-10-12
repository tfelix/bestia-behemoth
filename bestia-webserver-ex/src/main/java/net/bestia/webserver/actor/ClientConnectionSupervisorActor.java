package net.bestia.webserver.actor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.webserver.messages.ClusterConnectionStatus;
import net.bestia.webserver.messages.ClusterConnectionStatus.State;

/**
 * Manages the socket connections to the clients. If a connection to the client
 * breaks and thus the actor is terminated this supervising actor will notify
 * the server.
 * 
 * If the connection to the server is lost he will disconnect all clients.
 * 
 * @author Thomas Felix
 *
 */
public class ClientConnectionSupervisorActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ObjectMapper objectMapper = new ObjectMapper();
	private ActorRef clusterUplink;
	private Set<ActorRef> clientConnections = new HashSet<>();

	private ClientConnectionSupervisorActor(ActorRef clusterObserverActor) {

		this.clusterUplink = Objects.requireNonNull(clusterObserverActor);
	}

	public static Props props(ActorRef clusterObserverActor) {
		return Props.create(new Creator<ClientConnectionSupervisorActor>() {
			private static final long serialVersionUID = 1L;

			public ClientConnectionSupervisorActor create() throws Exception {
				return new ClientConnectionSupervisorActor(clusterObserverActor);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(ClusterConnectionStatus.class, this::handleClusterConnectionChanged)
				.match(Terminated.class, this::handleTerminatedConnection)
				.build();
	}

	/**
	 * If a child has been terminated remove it.
	 *
	 */
	private void handleTerminatedConnection(Terminated t) {
		LOG.debug("Client connection actor {} terminated.", t.actor());
		clientConnections.remove(t.actor());
	}

	private void handleNewClientConnection() {

		final Props props = ClientConnectionActor.props(null, objectMapper, clusterUplink);
		final ActorRef connection = getContext().actorOf(props);
		getContext().watch(connection);
		clientConnections.add(connection);
	}

	private void handleClusterConnectionChanged(ClusterConnectionStatus statusMsg) {
		if (statusMsg.getState() == State.CONNECTED) {
			LOG.info("Cluster connected. Clients can now connect.");
			clusterUplink = statusMsg.getClusterConnection();
		} else {
			LOG.warning("Cluster gone away. Disconnecting all connected clients.");
		}
	}
}

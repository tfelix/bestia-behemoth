package net.bestia.zoneserver.actor.connection;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.zoneserver.service.ConnectionService;

/**
 * This actor holds the connection details of a client and is able to redirect
 * messages towards this client. It keeps track of the latency checks and
 * possibly disconnects the client if it does not reply in time.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ConnectionActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	private final static String ACTOR_NAME = "connection-%d";

	private final long accountId;
	private final ActorRef clientConnection;

	private final ConnectionService connectionService;

	@Autowired
	public ConnectionActor(Long accountId, ActorRef connection, ConnectionService connectionService) {

		this.clientConnection = Objects.requireNonNull(connection);
		this.accountId = Objects.requireNonNull(accountId);
		this.connectionService = Objects.requireNonNull(connectionService);

		getContext().watch(clientConnection);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LogoutMessage.class, this::handleLogout)
				.match(JsonMessage.class, this::onMessageForClient)
				.match(Terminated.class, this::onClientConnectionClosed)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		connectionService.connected(accountId, clientConnection.path().address());
	}

	@Override
	public void postStop() throws Exception {
		connectionService.disconnected(accountId);
	}

	/**
	 * Gets the unique actor name by its connected account id.
	 * 
	 * @param accId
	 *            The account ID.
	 * @return The unique name of the connection actor.
	 */
	public static String getActorName(long accId) {
		return String.format(ACTOR_NAME, accId);
	}

	/**
	 * Called if the client actor and thus its connection has been terminated.
	 * Connection actor must clean the server resources by terminating itself.
	 * 
	 */
	private void onClientConnectionClosed(Terminated msg) {
		getContext().stop(getSelf());
	}

	/**
	 * When we receive a special logout message we must forward it towards the webserver
	 * so he can terminate the client connection but we also terminate this
	 * actor since we are not needed anymore.
	 */
	private void handleLogout(LogoutMessage msg) {
		onMessageForClient(msg);
		getContext().stop(getSelf());
	}

	/**
	 * Message must be forwarded to the client webserver so the message can be
	 * received by the client.
	 */
	private void onMessageForClient(JsonMessage msg) {
		LOG.debug(String.format("Sending to client %d: %s", msg.getAccountId(), msg));
		clientConnection.tell(msg, getSelf());
	}
}

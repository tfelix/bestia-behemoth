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
import net.bestia.messages.internal.ClientConnectMessage;
import net.bestia.messages.internal.ClientConnectMessage.ConnectionState;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.zoneserver.actor.SpringExtension;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.LoginService;

/**
 * This actor holds the connection details of a client and is able to redirect
 * messages towards this client. It keeps track of the latency checks and
 * possibly disconnects the client if it does not reply in time.
 * 
 * The connection actor also periodically sends out messages towards the client
 * in order to receive ping replies (and to measure latency). The answer tough
 * are managed via a {@link LatencyManagerActor} who will save the last reply
 * and calculate the current latency.
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ClientConnectionActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final static String ACTOR_NAME = "connection-%d";

	private long accountId;
	private ActorRef clientSocket;

	private final LoginService loginService;
	private final ConnectionService connectionService;
	
	private final ActorRef zoneDigest;

	@Autowired
	public ClientConnectionActor(ConnectionService connectionService,
			LoginService loginService) {

		this.connectionService = Objects.requireNonNull(connectionService);
		this.loginService = Objects.requireNonNull(loginService);

		this.zoneDigest = SpringExtension.actorOf(getContext(), ZoneDigestActor.class);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LogoutMessage.class, this::handleLogout)
				.match(ClientConnectMessage.class, this::handleConnectionStatus)
				.match(JsonMessage.class, this::onMessageForClient)
				.match(Terminated.class, this::onClientConnectionClosed)
				.build();
	}

	@Override
	public void postStop() throws Exception {

		// Stop connection and clean up the associated entity actors.
		connectionService.disconnectAccount(accountId);
		loginService.logout(accountId);

		LOG.debug("Connection removed: {}, account: {}", getSelf().path(), accountId);
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
	 * Gets called if a new connection was established.
	 * 
	 * @param msg
	 */
	private void handleConnectionStatus(ClientConnectMessage msg) {
		if (msg.getState() == ConnectionState.CONNECTED) {
			startConnectionActor(msg);
		} else {
			// Simply forward message. And then kill ourself.
			clientSocket.tell(msg, getSelf());
			getContext().stop(getSelf());
		}
	}

	private void startConnectionActor(ClientConnectMessage msg) {

		if (msg.getState() != ConnectionState.CONNECTED) {
			return;
		}

		accountId = msg.getAccountId();
		clientSocket = msg.getWebserverRef();

		getContext().watch(clientSocket);

		SpringExtension.actorOf(getContext(), LatencyPingActor.class, accountId, clientSocket);

		LOG.debug("Connection established: {}, account: {}", getSelf().path(), accountId);
	}

	/**
	 * Called if the client actor and thus its connection has been terminated.
	 * Connection actor must clean the server resources by terminating itself.
	 * 
	 */
	private void onClientConnectionClosed(Terminated msg) {
		LOG.debug("Client {} has closed connection.", accountId);
		getContext().stop(getSelf());
	}

	/**
	 * When we receive a special logout message we must forward it towards the
	 * webserver so he can terminate the client connection but we also terminate
	 * this actor since we are not needed anymore and the webserver might be
	 * down and wont reply with a appropriate
	 * {@link ClientConnectMessage}.
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

		if (clientSocket == null) {
			LOG.warning("Can not send to client. Not actorRef set! MSG: {}", msg);
		} else {
			clientSocket.tell(msg, getSelf());
		}

	}
}

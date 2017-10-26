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
import net.bestia.messages.internal.FromClient;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.zoneserver.actor.SpringExtension;
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
	private final ActorRef clientIngest;

	@Autowired
	public ClientConnectionActor(LoginService loginService, ActorRef clientIngest) {

		this.loginService = Objects.requireNonNull(loginService);
		this.clientIngest = Objects.requireNonNull(clientIngest);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(FromClient.class, this::handleClientMessage)
				.match(JsonMessage.class, this::sendMessageToClient)
				.match(Terminated.class, m -> onClientConnectionClosed())
				.build();
	}

	@Override
	public void postStop() throws Exception {

		// Stop connection and clean up the associated entity actors.
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
	 * These kind of messages are coming from the client. Since we receive
	 * messages from both directions (server and from the client) as
	 * {@link JsonMessage} we must differentiate them. This is done by wrapping
	 * the message.
	 * 
	 * @param msg
	 *            The wrapped message if it comes from the client.
	 */
	private void handleClientMessage(FromClient msg) {
		Object payload = msg.getPayload();
		if (payload instanceof LoginAuthMessage) {
			
			handleLoginAuthRequest((LoginAuthMessage) payload);
			
		} else if (payload instanceof ClientConnectMessage) {
			
			handleConnectionStatus((ClientConnectMessage) payload);
			
		} else {
			
			throwIfNotAuthenticated();
			clientIngest.tell(payload, getSelf());
			
		}
	}

	/**
	 * This is the very first message the get from the system asking us to
	 * authenticate.
	 * 
	 * @param msg
	 */
	private void handleLoginAuthRequest(LoginAuthMessage msg) {

		// If we are already authenticated we must ignore this message as this
		// message contains an untrusted account id and might be spoofed in
		// order to inject data into another connection actor.
		if (accountId != 0) {
			LOG.warning("Account is already authenticated. Ignoring new request.");
			return;
		}

		// At first this points to the auth actor.
		clientSocket = getSender();
		getContext().watch(clientSocket);

		final ActorRef authRequest = SpringExtension.actorOf(getContext(), LoginAuthActor.class);
		authRequest.tell(msg, getSelf());
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
			onClientConnectionClosed();
		}
	}

	private void startConnectionActor(ClientConnectMessage msg) {

		accountId = msg.getAccountId();

		// Cleanup of we are wired to another actor.
		if (clientSocket != null) {
			getContext().unwatch(clientSocket);
		}

		clientSocket = msg.getWebserverRef();
		getContext().watch(clientSocket);

		//SpringExtension.actorOf(getContext(), LatencyPingActor.class, accountId, clientSocket);

		LOG.debug("Connection established: {}, account: {}", getSelf().path(), accountId);
	}

	/**
	 * Called if the client actor and thus its connection has been terminated.
	 * Connection actor must clean the server resources by terminating itself.
	 * 
	 */
	private void onClientConnectionClosed() {
		LOG.debug("Socket actor account {} has terminated.", accountId);
		getContext().stop(getSelf());
	}

	/**
	 * Message must be forwarded to the client webserver so the message can be
	 * received by the client.
	 */
	private void sendMessageToClient(JsonMessage msg) {

		LOG.debug(String.format("Sending to client %d: %s", msg.getAccountId(), msg));

		if (clientSocket == null) {
			LOG.warning("Can not send to client. Not actorRef set! MSG: {}", msg);
		} else {
			clientSocket.tell(msg, getSelf());
		}
	}

	/**
	 * Checks if the user has already authenticated. This is important to do
	 * otherwise forged messages from the webserver could trick the actor into
	 * beliving the client has alreads authed. MUST be called before every
	 * method invocation other then auth itself.
	 * 
	 * @return
	 */
	private void throwIfNotAuthenticated() {
		if (0 == accountId) {
			throw new IllegalStateException("Client connection not authenticated.");
		}
	}
}

package net.bestia.zoneserver.actor.connection;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.messages.misc.PingMessage;
import net.bestia.zoneserver.service.ConnectionService;
import net.bestia.zoneserver.service.LatencyService;
import scala.concurrent.duration.Duration;

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
public class ConnectionActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final static String ACTOR_NAME = "connection-%d";
	private final static String LATENCY_REQUEST_MSG = "latency";
	private final static int CLIENT_TIMEOUT_MS = 30 * 1000;

	private final long accountId;
	private final ActorRef clientConnection;

	private final ConnectionService connectionService;
	private final LatencyService latencyService;
	private boolean isFirstPing = true;

	private final Cancellable latencyTick = getContext().getSystem().scheduler().schedule(
			Duration.create(2, TimeUnit.SECONDS),
			Duration.create(5, TimeUnit.SECONDS),
			getSelf(), LATENCY_REQUEST_MSG, getContext().dispatcher(), null);

	@Autowired
	public ConnectionActor(
			Long accountId,
			ActorRef connection,
			ConnectionService connectionService,
			LatencyService latencyService) {

		this.clientConnection = Objects.requireNonNull(connection);
		this.accountId = Objects.requireNonNull(accountId);
		this.connectionService = Objects.requireNonNull(connectionService);
		this.latencyService = Objects.requireNonNull(latencyService);

		getContext().watch(clientConnection);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LogoutMessage.class, this::handleLogout)
				.match(JsonMessage.class, this::onMessageForClient)
				.match(Terminated.class, this::onClientConnectionClosed)
				.matchEquals(LATENCY_REQUEST_MSG, msg -> onLatencyRequest())
				.build();
	}

	@Override
	public void preStart() throws Exception {
		connectionService.connected(accountId, clientConnection.path().address());
	}

	@Override
	public void postStop() throws Exception {
		latencyTick.cancel();

		// TODO Handle the server ressource cleanup.
		connectionService.disconnected(accountId);
		latencyService.delete(accountId);

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
	 * The client is send a latency request message.
	 */
	private void onLatencyRequest() {

		if (isFirstPing) {
			final long now = System.currentTimeMillis();
			latencyService.addLatency(accountId, now, now + 10);
			isFirstPing = false;
		}

		// Check how many latency requests we have missed.
		long lastReply = latencyService.getLastClientReply(accountId);
		long dLastReply = System.currentTimeMillis() - lastReply;

		if (lastReply > 0 && dLastReply > CLIENT_TIMEOUT_MS) {
			// Connection seems to have dropped. Signal the server that the
			// client has disconnected and terminate.
			getContext().stop(getSelf());
		} else {
			final PingMessage ping = new PingMessage(accountId);
			onMessageForClient(ping);
		}
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
	 * When we receive a special logout message we must forward it towards the
	 * webserver so he can terminate the client connection but we also terminate
	 * this actor since we are not needed anymore and the webserver might be
	 * down and wont reply with a appropriate
	 * {@link ClientConnectionStatusMessage}.
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

package net.bestia.zoneserver.actor.connection;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.messages.misc.PingMessage;
import net.bestia.messages.misc.PongMessage;
import net.bestia.zoneserver.service.LatencyService;
import net.bestia.zoneserver.service.LoginService;
import scala.concurrent.duration.Duration;

/**
 * 
 * @author Thomas Felix
 *
 */
@Component
@Scope("prototype")
public class ConnectionActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final static String ACTOR_NAME = "connection-%d";

	private static final String LATENCY_REQUEST_MSG = "latency";
	private static final int MAX_LATENCY_MISSING = 4;

	private final Cancellable tick = getContext().getSystem().scheduler().schedule(
			Duration.create(2, TimeUnit.SECONDS),
			Duration.create(5, TimeUnit.SECONDS),
			getSelf(), LATENCY_REQUEST_MSG, getContext().dispatcher(), null);

	private final long accountId;
	private ActorRef clientWebserver;
	private final LatencyService latencyService;
	private final LoginService loginService;

	private int missedLatencyCounter = 0;	

	@Autowired
	public ConnectionActor(Long accountId, 
			LatencyService latencyService,
			LoginService loginService) {

		this.accountId = Objects.requireNonNull(accountId);
		this.latencyService = Objects.requireNonNull(latencyService);
		this.loginService = Objects.requireNonNull(loginService);
	}

	@Override
	public void postStop() {
		tick.cancel();
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(JsonMessage.class, this::handleClientMessage)
				.match(PongMessage.class, this::handlePongMessage)
				.match(ClientConnectionStatusMessage.class, this::handleClientConnectionMessage)
				.match(LogoutMessage.class, this::handleLogout)
				.matchEquals(LATENCY_REQUEST_MSG, msg -> handleLatencyRequest())
				.build();
	}

	public static String getActorName(long accId) {
		return String.format(ACTOR_NAME, accId);
	}

	/**
	 * When we receive a logout message we must forward it towards the webserver
	 * so he can terminate the client connection but we also terminate this
	 * actor since we are not needed anymore.
	 */
	private void handleLogout(LogoutMessage msg) {
		clientWebserver.tell(msg, getSelf());
		getContext().stop(getSelf());
	}

	private void handleClientConnectionMessage(ClientConnectionStatusMessage msg) {

		if (msg.getState() == ConnectionState.CONNECTED) {
			LOG.debug("Client {} was connected to webserver: {}", msg.getAccountId(), msg.getWebserverRef().toString());
			clientWebserver = msg.getWebserverRef();
		} else {
			// We terminate.
			LOG.debug("Client {} was disconnected.", msg.getAccountId());
			getContext().stop(getSelf());
		}
	}

	/**
	 * The client is send a latency request message.
	 */
	private void handleLatencyRequest() {
		missedLatencyCounter++;

		if (missedLatencyCounter > MAX_LATENCY_MISSING) {
			// Connection seems to have dropped. Signal the server that the
			// client has disconnected and terminate.
			loginService.logout(accountId);
			getContext().stop(getSelf());
		} else {
			
			if(clientWebserver == null) {
				// Not yet identified. Abort.
				return;
			}
			
			final PingMessage ping = new PingMessage(accountId);
			clientWebserver.tell(ping, getSender());
		}
	}

	private void handlePongMessage(PongMessage msg) {
		missedLatencyCounter = 0;

		// Calculate the delta.
		final long delta = System.currentTimeMillis() - msg.getStart();
		latencyService.addLatency(accountId, delta);
	}

	/**
	 * Message must be forwarded to the client webserver so the message can be
	 * received by the client.
	 * 
	 * @param msg
	 */
	private void handleClientMessage(JsonMessage msg) {
		clientWebserver.tell(msg, getSelf());
	}
}

package bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import bestia.messages.AccountMessage;
import bestia.messages.JsonMessage;
import bestia.messages.client.ClientConnectMessage;
import bestia.messages.client.ClientConnectMessage.ConnectionState;
import bestia.messages.login.LoginAuthMessage;
import bestia.messages.login.LoginAuthReplyMessage;
import bestia.messages.login.LoginState;
import bestia.messages.login.LogoutMessage;
import bestia.webserver.messages.web.ClientPayloadMessage;
import scala.concurrent.duration.Duration;

public class ClientActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private Cancellable deathTimer = getContext().system()
			.scheduler()
			.scheduleOnce(
					Duration.create(10, TimeUnit.SECONDS),
					getSelf(), PoisonPill.getInstance(), getContext().dispatcher(), null);

	protected final ActorRef uplink;
	protected final WebSocketSession session;
	protected final ObjectMapper mapper;

	private final AbstractActor.Receive unauthenticated;
	private final AbstractActor.Receive authenticated;

	/**
	 * Account id is set as soon as the connection gets confirmed from the
	 * server.
	 */
	private long accountId;

	public ClientActor(ActorRef uplink, ObjectMapper mapper, WebSocketSession session) {
		
		this.uplink = Objects.requireNonNull(uplink);
		this.mapper = Objects.requireNonNull(mapper);
		this.session = Objects.requireNonNull(session);

		// Setup the two behaviours.
		unauthenticated = receiveBuilder()
				.match(LoginAuthReplyMessage.class, this::handleLoginAuthReply)
				.match(ClientPayloadMessage.class, this::handleClientPayloadUnauthenticated)
				.build();

		authenticated = receiveBuilder()
				.match(LogoutMessage.class, this::handleServerLogout)
				.match(AccountMessage.class, this::sendToClient)
				.match(ClientPayloadMessage.class, this::handleClientPayload)
				.build();

	}

	public static Props props(ActorRef uplink, ObjectMapper mapper, WebSocketSession session) {
		return Props.create(new Creator<ClientActor>() {
			private static final long serialVersionUID = 1L;

			public ClientActor create() throws Exception {
				return new ClientActor(uplink, mapper, session);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return unauthenticated;
	}

	@Override
	public void postStop() throws Exception {

		// Send the server that we have closed the connection.
		// If the websocket session is still opened and we are terminated from
		// the akka side, close it here.
		if (session.isOpen()) {
			LOG.debug("Closing connection to {}.", session.getRemoteAddress().toString());
			try {
				session.close(CloseStatus.NORMAL);
			} catch (IOException e1) {
				// no op.
			}
		}

		final ClientConnectMessage ccsmsg = new ClientConnectMessage(
				accountId,
				ConnectionState.DISCONNECTED,
				getSelf());

		uplink.tell(ccsmsg, getSelf());
	}

	/**
	 * The server requests a logout.
	 */
	private void handleServerLogout(LogoutMessage msg) {

		// Send the message to the client like every other message.
		sendToClient(msg);

		// Kill ourself.
		getContext().stop(getSelf());
	}

	/**
	 * Payload is send from the client to the server.
	 * 
	 * @param payload
	 *            Payload data from the client.
	 * @throws IOException
	 */
	private void handleClientPayload(ClientPayloadMessage payload) throws IOException {
		// We only accept auth messages if we are not connected. Every other
		// message will disconnect the client.

		try {
			// Turn the text message into a bestia message.
			JsonMessage msg = mapper.readValue(payload.getMessage(), JsonMessage.class);

			// Regenerate the account id from this session. (we dont trust
			// the client to tell us the right account id).
			msg = msg.createNewInstance(accountId);

			LOG.debug("Client sending: {}.", msg.toString());
			uplink.tell(msg, getSelf());

		} catch (IOException e) {
			LOG.warning("Malformed message. Client: {}, Payload: {}, Error: {}.",
					session.getRemoteAddress(),
					payload,
					e.toString());
			throw e;
		}

	}

	/**
	 * Payload is send from the client to the server.
	 * 
	 * @param payload
	 *            Payload data from the client.
	 * @throws IOException
	 */
	private void handleClientPayloadUnauthenticated(ClientPayloadMessage payload) throws IOException {
		// We only accept auth messages if we are not connected. Every other
		// message will disconnect the client.
		final LoginAuthMessage loginReqMsg = mapper.readValue(payload.getMessage(), LoginAuthMessage.class);
		uplink.tell(loginReqMsg, getSelf());
	}

	/**
	 * If the server accepted the login we will propagate this to our parent and
	 * create the permanent client socket.
	 */
	private void handleLoginAuthReply(LoginAuthReplyMessage msg) throws Exception {
		// Check how the login state was given.
		if (msg.getLoginState() == LoginState.ACCEPTED) {
			
			// This acc id is verified by the server.
			this.accountId = msg.getAccountId();
			
			getContext().become(authenticated);

			// Announce to the server that we have a fully connected client.
			final ClientConnectMessage cccm = new ClientConnectMessage(accountId,
					ConnectionState.CONNECTED,
					getSelf());
			uplink.tell(cccm, getSelf());

			// Send the client the login message. This must be done when the server
			// has completed the registration of the client and awaits data now.
			sendToClient(msg);
			
		} else {
			// We were denied login. Send to client then stop actor.
			sendToClient(msg);
			getContext().stop(getSelf());
		}

		deathTimer.cancel();
		deathTimer = null;
	}

	protected void sendToClient(AccountMessage message) {
		// Send the payload to the client.
		try {
			final String payload = mapper.writeValueAsString(message);
			LOG.debug("Server sending: {}.", payload);
			session.sendMessage(new TextMessage(payload));
		} catch (IOException | IllegalStateException e) {
			// Could not send to client.
			LOG.error("Could not send message: {}.", message.toString(), e);
			getContext().stop(getSelf());
		}
	}
}

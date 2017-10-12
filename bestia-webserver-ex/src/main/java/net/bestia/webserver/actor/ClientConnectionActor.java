package net.bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.cluster.ClientConnectionStatusMessage;
import net.bestia.messages.cluster.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.messages.login.LogoutMessage;

/**
 * This actor will handle all the message exchange with the websocket. When a
 * message from the zone cluster is received it will forward the message to the
 * client or if client messages are incoming it will handle them and forward
 * them into the system.
 * 
 * @author Thomas Felix
 *
 */
public class ClientConnectionActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final WebSocketSession session;
	private final ObjectMapper mapper;
	private final ActorRef uplinkRouter;

	private boolean isAuthenticated = false;

	/**
	 * Account id is set as soon as the connection gets confirmed from the
	 * server.
	 */
	private long accountId = 0;

	/**
	 * Ctor.
	 * 
	 * @param session
	 *            The websocket session attached to this connection.
	 * @param mapper
	 *            An jackson json mapper.
	 */
	public ClientConnectionActor(WebSocketSession session, ObjectMapper mapper, ActorRef uplinkRouter) {

		this.session = Objects.requireNonNull(session, "Session can not be null.");
		this.mapper = Objects.requireNonNull(mapper, "Mapper can not be null.");
		this.uplinkRouter = Objects.requireNonNull(uplinkRouter);
	}

	/**
	 * Akka props helper method.
	 * 
	 * @param session
	 * @param mapper
	 * @return
	 */
	public static Props props(WebSocketSession session, ObjectMapper mapper, ActorRef uplinkRouter) {
		return Props.create(new Creator<ClientConnectionActor>() {
			private static final long serialVersionUID = 1L;

			public ClientConnectionActor create() throws Exception {
				return new ClientConnectionActor(session, mapper, uplinkRouter);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LoginAuthReplyMessage.class, this::handleLoginAuth)
				.match(LogoutMessage.class, this::handleServerLogout)
				.match(AccountMessage.class, this::handleServerPayload)
				.match(String.class, this::handleClientPayload)
				.build();
	}

	private void handleServerPayload(AccountMessage message) throws Exception {
		// Send the payload to the client.
		final String payload = mapper.writeValueAsString(message);
		LOG.debug("Server sending: {}.", payload);
		session.sendMessage(new TextMessage(payload));
	}

	/**
	 * The server requests a logout.
	 * 
	 * @throws Exception
	 */
	private void handleServerLogout(LogoutMessage msg) throws Exception {

		LOG.debug("Client connection received server logout.");

		handleServerPayload(msg);

		getContext().stop(getSelf());
	}

	/**
	 * Payload is send from the client to the server.
	 * 
	 * @param payload
	 *            Payload data from the client.
	 */
	private void handleClientPayload(String payload) {
		// We only accept auth messages if we are not connected. Every other
		// message will disconnect the client.
		if (!isAuthenticated) {
			try {
				final LoginAuthMessage loginReqMsg = mapper.readValue(payload, LoginAuthMessage.class);

				// Send the LoginRequest to the cluster.
				// Somehow centralize the names of the actors.
				uplinkRouter.tell(loginReqMsg, getSelf());

			} catch (IOException e) {
				// Wrong message. Terminate connection.
				LOG.warning("Client {} send wrong auth message. Payload was: {}.",
						session.getRemoteAddress(),
						payload);
				getContext().stop(getSelf());
			}
		} else {
			try {
				// Turn the text message into a bestia message.
				JsonMessage msg = mapper.readValue(payload, JsonMessage.class);

				// Regenerate the account id from this session. (we dont trust
				// the client to tell us the right account id).
				msg = msg.createNewInstance(accountId);

				LOG.debug("Client sending: {}.", msg.toString());
				uplinkRouter.tell(msg, getSelf());

			} catch (IOException e) {
				LOG.warning("Malformed message. Client: {}, Payload: {}, Error: {}.",
						session.getRemoteAddress(),
						payload,
						e.toString());
				getContext().stop(getSelf());
			}
		}
	}

	/**
	 * Either stores the connection details if server accepted the connection or
	 * close the websocket.
	 */
	private void handleLoginAuth(LoginAuthReplyMessage msg) throws Exception {
		// Check how the login state was given.
		if (msg.getLoginState() == LoginState.ACCEPTED) {

			isAuthenticated = true;
			accountId = msg.getAccountId();

			// Also announce to client the login success.
			handleServerPayload(msg);

			// Announce to the server that client is now fully connected.
			final ClientConnectionStatusMessage ccsmsg = new ClientConnectionStatusMessage(
					accountId,
					ConnectionState.CONNECTED,
					getSelf());
			uplinkRouter.tell(ccsmsg, getSelf());

		} else {
			handleServerPayload(msg);
			getContext().stop(getSelf());
		}
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		// If the websocket session is still opened and we are terminated from
		// the akka side, close it here.
		if (session.isOpen()) {
			session.close(CloseStatus.NORMAL);
		}
	}
}

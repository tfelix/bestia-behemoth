package net.bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.cluster.client.ClusterClient;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.messages.login.LogoutMessage;

public class ClientSocketActor extends AbstractActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final ActorRef uplink;
	private final ObjectMapper mapper;
	private final WebSocketSession session;

	private boolean isAuthenticated = false;

	/**
	 * Account id is set as soon as the connection gets confirmed from the
	 * server.
	 */
	private long accountId = 0;

	public ClientSocketActor(WebSocketSession session, ObjectMapper mapper, ActorRef uplink) {

		this.mapper = Objects.requireNonNull(mapper);
		this.uplink = Objects.requireNonNull(uplink);
		this.session = Objects.requireNonNull(session);

		// Watch the uplink in case it goes down.
		getContext().watch(uplink);
	}

	public static Props props(WebSocketSession session, ObjectMapper mapper, ActorRef uplink) {
		return Props.create(new Creator<ClientSocketActor>() {
			private static final long serialVersionUID = 1L;

			public ClientSocketActor create() throws Exception {
				return new ClientSocketActor(session, mapper, uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleUplinkClosed)
				.match(LoginAuthReplyMessage.class, this::handleLoginAuth)
				.match(LogoutMessage.class, this::handleServerLogout)
				.match(AccountMessage.class, this::sendToClient)
				.match(String.class, this::handleClientPayload)
				.build();
	}

	@Override
	public void postStop() throws Exception {

		// Send the server that we have closed the connection.
		closeSocketConnection(CloseStatus.NORMAL);
		
		// If we were fully connected, disconnect from the server aswell.
		if (accountId == 0 || !isAuthenticated) {
			return;
		}
		
		final ClientConnectionStatusMessage ccsmsg = new ClientConnectionStatusMessage(
				accountId,
				ConnectionState.DISCONNECTED,
				getSelf());
		uplink.tell(ccsmsg, getSelf());
	}

	private void handleUplinkClosed(Terminated t) {
		System.err.println("Connection to remote system lost.");
	}

	/**
	 * The server requests a logout.
	 */
	private void handleServerLogout(LogoutMessage msg) {

		// Send the message to the client like every other message.
		sendToClient(msg);

		closeSocketConnection(CloseStatus.NORMAL);
		
		// Kill ourself.
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
				uplink.tell(loginReqMsg, getSelf());

			} catch (IOException e) {
				// Wrong message. Terminate connection.
				LOG.warning("Client {} send wrong auth message. Payload was: {}.",
						session.getRemoteAddress(),
						payload,
						e);
				closeSocketConnection(CloseStatus.PROTOCOL_ERROR);
			}
		} else {
			try {
				// Turn the text message into a bestia message.
				JsonMessage msg = mapper.readValue(payload, JsonMessage.class);

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
				closeSocketConnection(CloseStatus.BAD_DATA);
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
			sendToClient(msg);

			// Announce to the server that client is now fully connected.
			final ClientConnectionStatusMessage ccsmsg = new ClientConnectionStatusMessage(
					accountId,
					ConnectionState.CONNECTED,
					getSelf());
			uplink.tell(ccsmsg, getSelf());

		} else {
			sendToClient(msg);
			closeSocketConnection(CloseStatus.PROTOCOL_ERROR);
		}
	}

	private void sendToClient(AccountMessage message) {
		// Send the payload to the client.
		try {
			final String payload = mapper.writeValueAsString(message);
			LOG.debug("Server sending: {}.", payload);
			session.sendMessage(new TextMessage(payload));
		} catch (JsonProcessingException e) {
			LOG.error("Could not serialize server message: {}.", message.toString());
		} catch (IOException e) {
			// Could not send to client.
			closeSocketConnection(CloseStatus.NORMAL);
			getContext().stop(getSelf());
		}

	}

	private void closeSocketConnection(CloseStatus status) {

		// If the websocket session is still opened and we are terminated from
		// the akka side, close it here.
		if (session.isOpen()) {
			LOG.debug("Closing connection to {}.", session.getRemoteAddress().toString());
			try {
				session.close(status);
			} catch (IOException e1) {
				// no op.
			}
		}
	}
}

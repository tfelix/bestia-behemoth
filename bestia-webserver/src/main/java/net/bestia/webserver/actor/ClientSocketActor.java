package net.bestia.webserver.actor;

import java.io.IOException;

import org.springframework.web.socket.CloseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LogoutMessage;
import net.bestia.webserver.messages.web.ZoneConnectionAccepted;

public class ClientSocketActor extends BaseSocketActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	/**
	 * Account id is set as soon as the connection gets confirmed from the
	 * server.
	 */
	private final long accountId;

	private LoginAuthReplyMessage loginReply;

	public ClientSocketActor(ZoneConnectionAccepted zoneConnection, ObjectMapper mapper, ActorRef uplink) {
		super(uplink, mapper, zoneConnection.getSession());

		this.accountId = zoneConnection.getLoginMessage().getAccountId();

		// Watch the uplink in case it goes down.
		// FIXME Das geht halt nie down. Weil richtiger uplink gekapselt.
		getContext().watch(uplink);

		loginReply = zoneConnection.getLoginMessage();
	}

	public static Props props(ZoneConnectionAccepted zoneConnection, ObjectMapper mapper, ActorRef uplink) {
		return Props.create(new Creator<ClientSocketActor>() {
			private static final long serialVersionUID = 1L;

			public ClientSocketActor create() throws Exception {
				return new ClientSocketActor(zoneConnection, mapper, uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Terminated.class, this::handleUplinkClosed)
				.match(LogoutMessage.class, this::handleServerLogout)
				.match(AccountMessage.class, this::sendToClient)
				.match(String.class, this::handleClientPayload)
				.build();
	}

	@Override
	public void preStart() throws Exception {
		// Announce to the server that we have a connected client.

		final ClientConnectionStatusMessage cccm = new ClientConnectionStatusMessage(accountId,
				ConnectionState.CONNECTED,
				getSelf());

		uplink.tell(cccm, getSelf());

		// Send the client the login message. This must be done when the server
		// has completed the registration of the client and awaits data now.
		sendToClient(loginReply);
		
		// Save memory now.
		loginReply = null;
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
	private void handleClientPayload(String payload) throws IOException {
		// We only accept auth messages if we are not connected. Every other
		// message will disconnect the client.

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
			throw e;
		}

	}
}

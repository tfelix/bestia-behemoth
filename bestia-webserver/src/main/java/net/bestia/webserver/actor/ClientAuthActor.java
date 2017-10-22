package net.bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.webserver.messages.web.ZoneConnectionAccepted;
import scala.concurrent.duration.Duration;

/**
 * Handles the client auth handshake. If the client sends no auth packet during
 * a defined time windows the actor stops.
 * 
 * @author Thomas
 *
 */
public class ClientAuthActor extends BaseSocketActor {

	final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	protected static final String TICK_MSG = "net.bestia.TICK_MSG";
	private Cancellable ticker = getContext().system()
			.scheduler()
			.scheduleOnce(
					Duration.create(5, TimeUnit.SECONDS),
					getSelf(), PoisonPill.getInstance(), getContext().dispatcher(), null);

	private final String uid;

	/**
	 * Flag if the websocket should be closed upon actor termination. Socket
	 * must be kept open of the login process was successful.
	 */
	private boolean dontCloseSocket = false;

	public ClientAuthActor(String uid, WebSocketSession session, ObjectMapper mapper, ActorRef uplink) {
		super(uplink, mapper, session);

		this.uid = Objects.requireNonNull(uid);
	}

	public static Props props(String uid, WebSocketSession session, ObjectMapper mapper, ActorRef uplink) {
		return Props.create(new Creator<ClientAuthActor>() {
			private static final long serialVersionUID = 1L;

			public ClientAuthActor create() throws Exception {
				return new ClientAuthActor(uid, session, mapper, uplink);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(LoginAuthReplyMessage.class, this::handleLoginAuth)
				.match(String.class, this::handleClientPayload)
				.build();
	}

	@Override
	public void postStop() throws Exception {
		
		ticker.cancel();
		
		// If the websocket session is still opened and we are terminated from
		// the akka side, close it here.
		if (session.isOpen() && !dontCloseSocket) {
			LOG.debug("Closing connection to {}.", session.getRemoteAddress().toString());
			try {
				session.close(CloseStatus.PROTOCOL_ERROR);
			} catch (IOException e) {
				// no op.
			}
		}
	}

	/**
	 * Payload is send from the client to the server.
	 * 
	 * @param payload
	 *            Payload data from the client.
	 * @throws IOException
	 */
	protected void handleClientPayload(String payload) throws IOException {
		// We only accept auth messages if we are not connected. Every other
		// message will disconnect the client.

		try {
			final LoginAuthMessage loginReqMsg = mapper.readValue(payload, LoginAuthMessage.class);
			// Send the LoginRequest to the cluster.
			uplink.tell(loginReqMsg, getSelf());

		} catch (IOException e) {
			// Wrong message. Terminate connection.
			LOG.warning("Client {} send wrong auth message. Payload was: {}.",
					session.getRemoteAddress(),
					payload,
					e);
			throw e;
		}
	}

	/**
	 * If the server accepted the login we will propagate this to our parent and
	 * create the permanent client socket.
	 */
	private void handleLoginAuth(LoginAuthReplyMessage msg) throws Exception {
		// Check how the login state was given.
		if (msg.getLoginState() == LoginState.ACCEPTED) {
			final ZoneConnectionAccepted zoneMsg = new ZoneConnectionAccepted(msg, uid, session);
			getContext().parent().tell(zoneMsg, getSelf());
			dontCloseSocket = true;
		} else {
			// We were denied login. Send to client then stop actor.
			sendToClient(msg);
		}

		getContext().stop(getSelf());
	}
}

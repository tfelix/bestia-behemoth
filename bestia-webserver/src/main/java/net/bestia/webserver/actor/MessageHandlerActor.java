package net.bestia.webserver.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.Deploy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.JacksonMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage;
import net.bestia.messages.internal.ClientConnectionStatusMessage.ConnectionState;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.server.AkkaCluster;

/**
 * This actor will handle all the message exchange with the websockt. When a
 * message from the zone cluster is received it will forward the message to the
 * client or if client messages are incoming it will handle them and forward
 * them into the system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MessageHandlerActor extends UntypedActor {

	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);

	private final WebSocketSession session;
	private final ObjectMapper mapper;

	private final ActorRef mediator = DistributedPubSub.get(getContext().system()).mediator();

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
	public MessageHandlerActor(WebSocketSession session, ObjectMapper mapper) {

		this.session = Objects.requireNonNull(session, "Session can not be null.");
		this.mapper = Objects.requireNonNull(mapper, "Mapper can not be null.");

	}

	/**
	 * Akka props helper method.
	 * 
	 * @param session
	 * @param mapper
	 * @return
	 */
	public static Props props(WebSocketSession session, ObjectMapper mapper) {
		return Props.create(new Creator<MessageHandlerActor>() {
			private static final long serialVersionUID = 1L;

			public MessageHandlerActor create() throws Exception {
				return new MessageHandlerActor(session, mapper);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (message instanceof LoginAuthReplyMessage) {

			handleLoginAuth((LoginAuthReplyMessage) message);

		} else if (message instanceof AccountMessage) {

			sendToClient((AccountMessage) message);

		} else if (message instanceof String) {

			handlePayload((String) message);

		} else {
			unhandled(message);
		}
	}

	private void sendToClient(AccountMessage message) throws Exception {
		// Send the payload to the client.
		final String payload = mapper.writeValueAsString(message);
		session.sendMessage(new TextMessage(payload));
	}

	private void handlePayload(String payload) {
		// We only accept auth messages.
		if (!isAuthenticated) {
			try {
				final LoginAuthMessage loginReqMsg = mapper.readValue(payload, LoginAuthMessage.class);

				// Send the LoginRequest to the cluster.
				// Somehow centralize the names of the actors.
				mediator.tell(getClusterMessage(loginReqMsg), getSelf());

			} catch (IOException e) {
				// Wrong message. Terminate connection.
				LOG.warning("Client {} send wrong auth message. Payload was: {}.",
						session.getRemoteAddress(),
						payload);
				closeSession(CloseStatus.PROTOCOL_ERROR);
			}
		} else {
			try {
				// Turn the text message into a bestia message.
				final JacksonMessage msg = mapper.readValue(payload, JacksonMessage.class);
				LOG.debug("Client sending: {}.", msg.toString());
				mediator.tell(getClusterMessage(msg), getSelf());

			} catch (IOException e) {
				LOG.warning("Malformed message. Client: {}, Payload: {}, Error: {}.", 
						session.getRemoteAddress(), 
						payload, 
						e.toString());
				closeSession(CloseStatus.BAD_DATA);
			}
		}
	}

	private void handleLoginAuth(LoginAuthReplyMessage msg) throws Exception {
		// Check how the login state was given.
		if (msg.getLoginState() == LoginState.ACCEPTED) {
			isAuthenticated = true;
			accountId = msg.getAccountId();
			// Announce to the cluster that we have a new connected user.
			// Welcome my friend. :)
			final ClientConnectionStatusMessage ccsmsg = new ClientConnectionStatusMessage(
					msg.getAccountId(),
					ConnectionState.CONNECTED,
					getSelf());
			mediator.tell(getClusterMessage(ccsmsg), getSelf());
			
			// Also announce to client the login success.
			sendToClient(msg);
			
		} else {
			closeSession(CloseStatus.PROTOCOL_ERROR);
		}
	}

	private void closeSession(CloseStatus status) {
		LOG.debug("Closing connection to {}.", session.getRemoteAddress().toString());

		try {
			session.close(status);
		} catch (IOException e1) {
			// no op.
		}

		// Kill ourself.
		getSelf().tell(PoisonPill.getInstance(), getSelf());
	}

	@Override
	public void postStop() throws Exception {
		super.postStop();

		// If we were connected, disconnect from the server.
		if (accountId != 0) {
			final ClientConnectionStatusMessage ccsmsg = new ClientConnectionStatusMessage(
					accountId,
					ConnectionState.DISCONNECTED,
					getSelf());
			mediator.tell(getClusterMessage(ccsmsg), getSelf());
		}
	}

	/**
	 * Generates a clustered message to the pub sub mediator in the akka
	 * cluster. (message gets send to the distributed cluster).
	 * 
	 * @return The message for the cluster.
	 */
	private Object getClusterMessage(Object msg) {
		return new DistributedPubSubMediator.Publish(AkkaCluster.CLUSTER_PUBSUB_TOPIC, msg);
	}

}

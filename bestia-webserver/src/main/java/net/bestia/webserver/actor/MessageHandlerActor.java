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
import net.bestia.messages.ClientMessage;
import net.bestia.messages.Message;
import net.bestia.messages.login.LoginAuthMessage;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LoginState;
import net.bestia.server.AkkaCluster;
import net.bestia.server.BestiaActorContext;

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
	 * Ctor.
	 * 
	 * @param session
	 *            The websocket session attached to this connection.
	 * @param mapper
	 *            An jackson json mapper.
	 */
	public MessageHandlerActor(WebSocketSession session, ObjectMapper mapper, BestiaActorContext bestiaCtx) {

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
	public static Props props(WebSocketSession session, ObjectMapper mapper, BestiaActorContext bestiaCtx) {
		return Props.create(new Creator<MessageHandlerActor>() {
			private static final long serialVersionUID = 1L;

			public MessageHandlerActor create() throws Exception {
				return new MessageHandlerActor(session, mapper, bestiaCtx);
			}
		}).withDeploy(Deploy.local());
	}

	@Override
	public void onReceive(Object message) throws Exception {
		
		if (message instanceof LoginAuthReplyMessage) {

			// Check how the login state was given.
			final LoginAuthReplyMessage msg = (LoginAuthReplyMessage) message;

			if (msg.getLoginState() == LoginState.ACCEPTED) {
				isAuthenticated = true;
			} else {
				closeSession(CloseStatus.PROTOCOL_ERROR);
			}
		}

		if (message instanceof ClientMessage) {

			// Send the payload to the client.
			final String payload = mapper.writeValueAsString(message);
			session.sendMessage(new TextMessage(payload));

		} else if (message instanceof String) {

			final String payload = (String) message;

			// We only accept auth messages.
			if (!isAuthenticated) {
				try {
					final LoginAuthMessage loginReqMsg = mapper.readValue(payload, LoginAuthMessage.class);

					// Send the LoginRequest to the cluster.
					// Somehow centralize the names of the actors.
					mediator.tell(new DistributedPubSubMediator.Publish(AkkaCluster.CLUSTER_PUBSUB_TOPIC, loginReqMsg), getSelf());

				} catch (IOException e) {
					// Wrong message. Terminate connection.
					LOG.warning("Client {} send wrong first auth message. Payload was: {}.", session.getRemoteAddress(),
							payload);
					closeSession(CloseStatus.PROTOCOL_ERROR);
				}
			} else {
				try {
					// Turn the text message into a bestia message.
					final Message msg = mapper.readValue(payload, Message.class);
					LOG.debug("Client sending: {}.", msg.toString());
					mediator.tell(new DistributedPubSubMediator.Publish(AkkaCluster.CLUSTER_PUBSUB_TOPIC, msg), getSelf());

				} catch (IOException e) {
					LOG.warning("Malformed message. Client: {}. Payload: {}.", session.getRemoteAddress(), payload);
					closeSession(CloseStatus.BAD_DATA);
				}
			}
		} else {
			unhandled(message);
		}
	}

	private void closeSession(CloseStatus status) {
		LOG.warning("Closing connection.");

		try {
			session.close(status);
		} catch (IOException e1) {
			// no op.
		}

		getSelf().tell(PoisonPill.getInstance(), getSelf());
	}

}

package net.bestia.next.webserver.actor;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import net.bestia.model.dao.AccountDAO;
import net.bestia.next.actor.BestiaActorContext;
import net.bestia.next.actor.LoginActor;
import net.bestia.next.messages.AccountMessage;
import net.bestia.next.messages.LoginRequestMessage;

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
	private final BestiaActorContext bestiaCtx;

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
		this.bestiaCtx = Objects.requireNonNull(bestiaCtx, "BestiaCtx can not be null.");

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
		});
	}

	@Override
	public void onReceive(Object message) throws Exception {

		if (!(message instanceof String)) {
			unhandled(message);
			return;
		}

		final String payload = (String) message;

		// We only accept auth messages.
		if (!isAuthenticated) {
			try {
				final LoginRequestMessage loginReqMsg = mapper.readValue(payload, LoginRequestMessage.class);

				// TODO Send the LoginRequest to the cluster.
				final AccountDAO accountDao = bestiaCtx.getSpringContext().getBean(AccountDAO.class);
				final ActorRef loginActor = getContext().actorOf(LoginActor.props(accountDao));
				loginActor.tell(loginReqMsg, getSelf());

			} catch (IOException e) {
				// Wrong message. Terminate connection.
				LOG.warning("Client {} send wrong first auth message. Payload was: {}.", session.getRemoteAddress(),
						payload);
				LOG.warning("Closing connection.");
				try {
					session.close();
				} catch (IOException e1) {
					// no op.
				}
				return;
			}

			return;
		}

		try {
			// Turn the text message into a bestia message.
			final AccountMessage msg = mapper.readValue(payload, AccountMessage.class);

			// TODO Send the Message to the cluster.

		} catch (IOException e) {
			LOG.warning("Malformed message. Client: {}. Payload: {}.", session.getRemoteAddress(), payload);
			LOG.warning("Closing connection.");
			try {
				session.close();
			} catch (IOException e1) {
				// no op.
			}
		}

	}

}

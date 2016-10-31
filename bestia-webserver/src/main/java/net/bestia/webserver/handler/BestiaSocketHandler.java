package net.bestia.webserver.handler;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import net.bestia.webserver.actor.MessageHandlerActor;

/**
 * Handles the bestia websocket to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSocketHandler extends TextWebSocketHandler {

	private static final Logger LOG = LoggerFactory.getLogger(BestiaSocketHandler.class);

	private static final String ATTRIBUTE_ACTOR_REF = "actorRef";

	private final ObjectMapper mapper = new ObjectMapper();
	private ActorSystem actorSystem;

	/**
	 * Sets the actor system.
	 * 
	 * @param actorSystem
	 *            The used actor system.
	 */
	@Autowired
	public void setActorSystem(ActorSystem actorSystem) {
		this.actorSystem = actorSystem;
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) {
		LOG.trace("Incoming raw: {}", message.getPayload());

		final String payload = message.getPayload();

		final ActorRef actor = (ActorRef) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);

		// Should normally not happen.
		if (actor == null) {
			try {
				LOG.warn("No actor ref to websocket session attached: {}", session.getRemoteAddress().toString());
				session.close(CloseStatus.SERVER_ERROR);
			} catch (IOException e) {
				// no op.
			}
		}

		actor.tell(payload, ActorRef.noSender());
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		LOG.trace("New connection: {}.", session.getRemoteAddress().toString());

		// Setup the actor to access the zone server cluster.
		final ActorRef messageActor = actorSystem.actorOf(MessageHandlerActor.props(session, mapper));
		session.getAttributes().put(ATTRIBUTE_ACTOR_REF, messageActor);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		LOG.trace("Closed connection: {}.", session.getRemoteAddress().toString());

		// Kill the underlying akka actor.
		final ActorRef actor = (ActorRef) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);
		
		if(actor == null) {
			return;
		}
		
		actor.tell(PoisonPill.getInstance(), ActorRef.noSender());
	}
}

package net.bestia.next.webserver.handler;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import net.bestia.next.messages.AccountMessage;
import net.bestia.next.messages.LoginRequestMessage;
import net.bestia.next.webserver.akka.actor.MessageHandlerActor;

/**
 * Handles the bestia websocket to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSocketHandler extends TextWebSocketHandler {

	private static final Logger LOG = LogManager.getLogger(BestiaSocketHandler.class);

	private static final String ATTRIBUTE_AUTH = "hasAuthenticated";
	private static final String ATTRIBUTE_ACTOR_REF = "actorRef";
	
	private final ObjectMapper mapper = new ObjectMapper();

	private ActorSystem actorSystem;

	@Autowired
	public void setActorSystem(ActorSystem actorSystem) {
		this.actorSystem = actorSystem;
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) {
		LOG.trace("Incoming raw: {}", message.getPayload());
		
		final String payload = message.getPayload();

		// First message must be a login message. If there is another message
		// connection is killed.
		if (!session.getAttributes().containsKey(ATTRIBUTE_AUTH)) {
			try {
				final LoginRequestMessage loginReqMsg = mapper.readValue(payload, LoginRequestMessage.class);
				
				final ActorRef actor = (ActorRef) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);
				
				actor.tell(loginReqMsg, ActorRef.noSender());
				
			} catch (IOException e) {
				// Wrong message.
				LOG.warn("Client {} send wrong first auth message. Payload was: {}.", session.getRemoteAddress(), payload);
				LOG.warn("Closing connection.");
				try {
					session.close();
				} catch (IOException e1) {
					// no op.
				}
				return;
			}
		}

		try {
			// Turn the text message into a bestia message.
			final AccountMessage msg = mapper.readValue(message.getPayload(), AccountMessage.class);
			
			final ActorRef actor = (ActorRef) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);
			
			actor.tell(msg, ActorRef.noSender());
			
		} catch (IOException e) {
			LOG.warn("Malformed message. Client: {}. Payload: {}.", session.getRemoteAddress(), payload);
			LOG.warn("Closing connection.");
			try {
				session.close();
			} catch (IOException e1) {
				// no op.
			}
		}
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
		actor.tell(PoisonPill.getInstance(), ActorRef.noSender());
	}
}

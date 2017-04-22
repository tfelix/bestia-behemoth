package net.bestia.webserver.websocket;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import net.bestia.webserver.actor.WebserverActorApi;

/**
 * Handles the bestia websocket to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSocketHandler extends TextWebSocketHandler {

	private static final Logger LOG = LoggerFactory.getLogger(BestiaSocketHandler.class);

	private static final String ATTRIBUTE_ACTOR_REF = "actorRef";
	private final WebserverActorApi actorApi;

	@Autowired
	public BestiaSocketHandler(WebserverActorApi actorApi) {

		this.actorApi = Objects.requireNonNull(actorApi);
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) {
		LOG.trace("Incoming raw msg: {}", message.getPayload());

		final String payload = message.getPayload();
		final String sessionUid = (String) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);

		actorApi.handleClientMessage(sessionUid, payload);
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		LOG.debug("New connection: {}.", session.getRemoteAddress().toString());

		final String sessionUid = UUID.randomUUID().toString();
		actorApi.setupWebsocketConnection(sessionUid, session);
		session.getAttributes().put(ATTRIBUTE_ACTOR_REF, sessionUid);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		LOG.debug("Closed connection: {}.", session.getRemoteAddress().toString());

		// Kill the underlying akka actor.
		actorApi.closeWebsocketConnection((String) session.getAttributes().get(ATTRIBUTE_ACTOR_REF));
	}
}

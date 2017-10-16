package net.bestia.webserver.websocket;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import net.bestia.webserver.actor.WebserverActorApi;
import net.bestia.webserver.service.ConfigurationService;

/**
 * Handles the bestia websocket to the clients.
 * 
 * @author Thomas Felix
 *
 */
//@Component
public class BestiaSocketHandler extends TextWebSocketHandler {

	private static final Logger LOG = LoggerFactory.getLogger(BestiaSocketHandler.class);

	private static final String ATTRIBUTE_ACTOR_REF = "actorRef";
	private final WebserverActorApi actorApi;
	private final ConfigurationService config;

	@Autowired
	public BestiaSocketHandler(WebserverActorApi actorApi, ConfigurationService config) {

		this.actorApi = Objects.requireNonNull(actorApi);
		this.config = Objects.requireNonNull(config);
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

		if (!config.isConnectedToCluster()) {
			LOG.debug("Not connected to cluster. Deny connection.");
			session.close(CloseStatus.SERVER_ERROR);
			return;
		}

		final String sessionUid = UUID.randomUUID().toString();
		actorApi.setupWebsocketConnection(sessionUid, session);
		session.getAttributes().put(ATTRIBUTE_ACTOR_REF, sessionUid);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		LOG.debug("Closed connection: {}.", session.getRemoteAddress().toString());

		// Kill the underlying akka actor if there is a actor ref associated.
		final String actorRef = (String) session.getAttributes().get(ATTRIBUTE_ACTOR_REF);

		// Actor ref might be null if the server denied the connection and just
		// closed it before an actor was created.
		if (actorRef == null) {
			return;
		}

		actorApi.closeWebsocketConnection(actorRef);
	}
}

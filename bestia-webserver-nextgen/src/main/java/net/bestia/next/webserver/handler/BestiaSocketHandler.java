package net.bestia.next.webserver.handler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handles the bestia websocket to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaSocketHandler extends TextWebSocketHandler {
	
	private static final Logger LOG = LogManager.getLogger(BestiaSocketHandler.class);
	
	@Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
		LOG.trace("Incoming raw: {}", message.getPayload());
        // ...
    }
	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		LOG.trace("New connection: {}.", session.getRemoteAddress().toString());
		// ...
	}
	
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		LOG.trace("Closed connection: {}.", session.getRemoteAddress().toString());
		// ...
	}
}

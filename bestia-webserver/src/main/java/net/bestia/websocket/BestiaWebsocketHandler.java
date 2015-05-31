package net.bestia.websocket;

import java.io.IOException;
import net.bestia.messages.Message;
import net.bestia.websocket.Webserver.InterserverConnectionProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.config.service.WebSocketHandlerService;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebSocketHandlerService(path = "/api")
public class BestiaWebsocketHandler implements WebSocketHandler {

	private final static Logger log = LogManager.getLogger(BestiaWebsocketHandler.class);

	private final ObjectMapper mapper = new ObjectMapper();
	private final Webserver.InterserverConnectionProvider provider = InterserverConnectionProvider.getInstance();

	@Override
	public void onOpen(WebSocket webSocket) throws IOException {

		log.trace("onOpen called.");
	}

	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);

		final Message msg = mapper.readValue(message, Message.class);

		provider.getConnection().publish(msg);
	}

	@Override
	public void onClose(WebSocket webSocket) {
		log.trace("onClose called.");
	}

	@Override
	public void onError(WebSocket socket, WebSocketException ex) {
		log.debug("onError called.");
		log.error("Error sending data to client.", ex);
	}

	@Override
	public void onByteMessage(WebSocket arg0, byte[] arg1, int arg2, int arg3) throws IOException {
		// no op.

	}
}

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

		// See if the given credentials are correct.
		/*
		 * try { final String token = webSocket.resource().getRequest().getHeader("bestia_token"); final int accId =
		 * Integer.parseInt(webSocket.resource().getRequest().getHeader("bestia_acc_id"));
		 * 
		 * final BestiaConnection con = new BestiaConnection(webSocket, accId); cache.put(con);
		 * log.trace("{} - Connection opened.", webSocket.resource().getRequest().getRemoteAddr());
		 * 
		 * } catch(IllegalArgumentException ex) { log.warn("Can not get bestia login credentials: " +
		 * webSocket.resource().getRequest().toString()); closeSocket(webSocket); }
		 */
	}

	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);

		final Message msg = mapper.readValue(message, Message.class);

		// Find account id for this connection.
		// msg.setAccountId(cache.get(webSocket.resource().uuid()).getAccountId());
		provider.getConnection().sendMessage(msg);
	}

	@Override
	public void onClose(WebSocket webSocket) {
		log.trace("onClose called.");
		// cache.remove(webSocket.resource().uuid());
		// log.trace("Connection closed by client: {}", webSocket.resource().toString());
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

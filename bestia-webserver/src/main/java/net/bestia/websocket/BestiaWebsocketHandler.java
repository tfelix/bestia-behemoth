package net.bestia.websocket;

import java.io.IOException;
import java.util.HashMap;

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
	
	@Override
	public void onOpen(WebSocket webSocket) throws IOException {
		
		log.debug("==== onOpen called. ====");
		webSocket.close();
		
		// See if the given credentials are correct.
		/*try {
			final String token = webSocket.resource().getRequest().getHeader("bestia_token");
			final int accId = Integer.parseInt(webSocket.resource().getRequest().getHeader("bestia_acc_id"));
			
			final BestiaConnection con = new BestiaConnection(webSocket, accId);
			cache.put(con);
			log.trace("{} - Connection opened.", webSocket.resource().getRequest().getRemoteAddr());
			
		} catch(IllegalArgumentException ex) {
			log.warn("Can not get bestia login credentials: " + webSocket.resource().getRequest().toString());
			closeSocket(webSocket);
		}*/
	}


	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);
	}

	@Override
	public void onClose(WebSocket webSocket) {
		log.debug("==== onClose called. ====");
		//cache.remove(webSocket.resource().uuid());
		//log.trace("Connection closed by client: {}", webSocket.resource().toString());
	}

	@Override
	public void onError(WebSocket socket, WebSocketException ex) {
		log.debug("==== onError called. ====");
		log.error("Error sending data to client.", ex);
	}


	@Override
	public void onByteMessage(WebSocket arg0, byte[] arg1, int arg2, int arg3) throws IOException {
		// TODO Auto-generated method stub
		
	}
}

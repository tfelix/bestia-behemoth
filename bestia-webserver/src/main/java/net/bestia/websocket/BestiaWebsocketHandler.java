package net.bestia.websocket;

import java.io.IOException;

import javax.inject.Inject;

import net.bestia.messages.RequestLoginMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.config.service.WebSocketHandlerService;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandlerAdapter;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;

@WebSocketHandlerService(path = "/api")
public class BestiaWebsocketHandler extends WebSocketHandlerAdapter {

	private final static Logger log = LogManager.getLogger(BestiaWebsocketHandler.class);
	private final BestiaConnectionProvider provider = BestiaConnectionProvider.getInstance();

	@Inject
	private AtmosphereResourceFactory resourceFactory;

	@Override
	public void onOpen(WebSocket webSocket) throws IOException {
		log.trace("onOpen called.");

		// New connection incoming. Check if login is ok.
		final int accountId = 1;
		final String token = "test-1234-5678";
		if (provider.getLoginCheckBlocker().isAuthenticated(accountId, token)) {
			// Since login is ok we must now be prepared to receive zone messages for this account connection.
			provider.addConnection(accountId, webSocket);

			// if so announce a new login.
			final RequestLoginMessage msg = new RequestLoginMessage(accountId);
			provider.publishInterserver(msg);
		} else {
			webSocket.close();
		}
	}

	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);

		// Forward message to the interserver.
		provider.publishInterserver(message);
	}

	@Override
	public void onClose(WebSocket webSocket) {
		log.trace("onClose called.");

		// Get the ID from this websocket connection.
		final String uuid = (String) webSocket.resource().getRequest()
				.getAttribute(ApplicationConfig.SUSPENDED_ATMOSPHERE_RESOURCE_UUID);
		final AtmosphereResource resource = resourceFactory.find(uuid);
		// long accountId = Long.parseLong(resource.getRequest().getHeader("X-Bestia-AccoundId"));

		final int accountId = 1;

		// TODO announce logout to the zone/interserver.

		// Remove connection from the provider.
		provider.removeConnection(accountId);
	}

	@Override
	public void onError(WebSocket socket, WebSocketException ex) {
		log.debug("onError called.");
		log.error("Error sending data to client.", ex);
	}
}

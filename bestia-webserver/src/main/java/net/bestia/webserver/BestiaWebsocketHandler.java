package net.bestia.webserver;

import java.io.IOException;

import javax.inject.Inject;

import net.bestia.messages.LoginBroadcastMessage;
import net.bestia.messages.LogoutBroadcastMessage;

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

		final String uuid = (String) webSocket.resource().getRequest()
				.getAttribute(ApplicationConfig.SUSPENDED_ATMOSPHERE_RESOURCE_UUID);
		final AtmosphereResource resource = resourceFactory.find(uuid);

		try {
			final long accountId = Long.parseLong(resource.getRequest().getHeader("X-Bestia-Account"));
			final String token = resource.getRequest().getHeader("X-Bestia-Token");

			if (provider.getLoginCheckBlocker().isAuthenticated(accountId, token)) {
				// Since login is ok we must now be prepared to receive zone messages for this account connection.
				provider.addConnection(accountId, webSocket);
				// if so announce a new login to the zones.
				final LoginBroadcastMessage msg = new LoginBroadcastMessage(accountId);
				provider.publishInterserver(msg);

				log.debug("Websocket connection accepted. account id: {}, token: {}", accountId, token);
			} else {
				webSocket.close();

				log.debug("Websocket connection not authenticated. account id: {}, token: {}", accountId, token);
			}
		} catch (NullPointerException | NumberFormatException ex) {
			webSocket.close();

			log.debug("No header or account id present. Closing websocket.");
			return;
		}
	}

	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);
		
		final long accountId = getAccountId(webSocket);
		
		if(accountId == 0) {
			return;
		}

		// Forward message to the interserver.
		provider.publishInterserver(accountId, message);
	}

	/**
	 * Regenerates the original account id of this connection.
	 * 
	 * @return
	 */
	private long getAccountId(WebSocket socket) {
		// Get the ID from this websocket connection.
		final String uuid = (String) socket.resource().getRequest()
				.getAttribute(ApplicationConfig.SUSPENDED_ATMOSPHERE_RESOURCE_UUID);
		final AtmosphereResource resource = resourceFactory.find(uuid);
		try {
			long accountId = Long.parseLong(resource.getRequest().getHeader("X-Bestia-Account"));
			return accountId;
		} catch (NullPointerException ex) {
			// Could not get account id. This should not happen.
			log.error("Could not get account id for existing connection.", ex);
			return 0L;
		}
	}

	@Override
	public void onClose(WebSocket webSocket) {

		// Get the ID from this websocket connection.
		long accountId = getAccountId(webSocket);
		
		if(accountId == 0) {
			return;
		}
		
		// Remove connection from the provider.
		provider.removeConnection(accountId);
		log.debug("Connection closed. Account id: {}", accountId);
		
		try {
			LogoutBroadcastMessage logoutMsg = new LogoutBroadcastMessage(accountId);
			provider.publishInterserver(logoutMsg);
		} catch (IOException ex) {
			log.warn("Could not properly broadcast a terminated connection.", ex);
		}
	}

	@Override
	public void onError(WebSocket socket, WebSocketException ex) {
		log.error("Error sending data to client.", ex);
	}
}

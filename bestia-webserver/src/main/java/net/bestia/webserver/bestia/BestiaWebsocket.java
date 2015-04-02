package net.bestia.webserver.bestia;

import java.io.IOException;
import java.util.HashMap;

import net.bestia.core.BestiaZoneserver;
import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.message.Message;
import net.bestia.core.message.jackson.SetupModule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.config.service.WebSocketHandlerService;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebSocketHandlerService(path = "/api")
public class BestiaWebsocket implements WebSocketHandler, BestiaConnectionInterface {
	
	private class BestiaConnectionCache {
		private final HashMap<String, BestiaConnection> uuidCache = new HashMap<>();
		private final HashMap<Integer, BestiaConnection> accIdCache = new HashMap<>();
		
		
		public synchronized void put(BestiaConnection connection) {
			uuidCache.put(connection.getUUID(), connection);
			accIdCache.put(connection.getAccountId(), connection);
		}
		
		public synchronized void remove(String uuid) {
			final BestiaConnection con = uuidCache.get(uuid);
			uuidCache.remove(uuid);
			accIdCache.remove(con.getAccountId());		
		}
		
		public synchronized void remove(Integer accId) {
			final BestiaConnection con = accIdCache.get(accId);
			accIdCache.remove(accId);
			uuidCache.remove(con.getUUID());		
		}
		
		public BestiaConnection get(Integer accId) {
			return accIdCache.get(accId);
		}
		
		public BestiaConnection get(String uuid) {
			return uuidCache.get(uuid);
		}
		
		public boolean containsAccountId(int accId) {
			return accIdCache.containsKey(accId);
		}
		
		public int size() {
			return uuidCache.size();
		}
	}

	private class BestiaConnection
	{
		private final WebSocket socket;
		private final int accountId;
		private final String uuid;
		
		public BestiaConnection(WebSocket socket, int accountId) {
			this.socket = socket;
			this.accountId = accountId;
			this.uuid = socket.resource().uuid();
		}
		
		public String getUUID() {
			return uuid;
		}

		public int getAccountId() {
			return accountId;
		}

		public WebSocket getSocket() {
			return socket;
		}
	}

	final static Logger log = LogManager.getLogger(BestiaWebsocket.class);

	private final BestiaZoneserver zone;
	private final ObjectMapper mapper = new ObjectMapper();
	private final BestiaConnectionCache cache = new BestiaConnectionCache();
	

	/**
	 * Ctor. Do some setup work and most importantly setup the connection to the zoneserver. When the websocket is
	 * created a bestia zone must already be started and registered to the {@link BestiaNettosphereConnection}.
	 */
	public BestiaWebsocket() {
		mapper.registerModule(new SetupModule());

		// Setup the connection with the server.
		BestiaNettosphereConnection.getInstance().setNettosphere(this);
		zone = BestiaNettosphereConnection.getInstance().getZone();
	}

	@Override
	public void onOpen(WebSocket webSocket) throws IOException {
		// See if the given credentials are correct.
		try {
			final String token = webSocket.resource().getRequest().getHeader("bestia_token");
			final int accId = Integer.parseInt(webSocket.resource().getRequest().getHeader("bestia_acc_id"));
			
			if (!zone.connect(accId, token)) {
				// No connection can be obtained.
				closeSocket(webSocket);
				return;
			}
			
			final BestiaConnection con = new BestiaConnection(webSocket, accId);
			cache.put(con);
			log.trace("{} - Connection opened.", webSocket.resource().getRequest().getRemoteAddr());
			
		} catch(IllegalArgumentException ex) {
			log.warn("Can not get bestia login credentials: " + webSocket.resource().getRequest().toString());
			closeSocket(webSocket);
		}
	}

	/**
	 * Closes the client connection and tells the client the reason.
	 * 
	 * @param socket
	 */
	private void closeSocket(WebSocket socket) {

	}

	@Override
	public void onTextMessage(WebSocket webSocket, String message) throws IOException {
		log.trace("MSG received: {}", message);

		final Message msg = mapper.readValue(message, Message.class);
		
		// Find account id for this connection.
		msg.setAccountId(cache.get(webSocket.resource().uuid()).getAccountId());
		
		zone.handleMessage(msg);
	}

	@Override
	public void onClose(WebSocket webSocket) {
		cache.remove(webSocket.resource().uuid());
		log.trace("Connection closed by client: {}", webSocket.resource().toString());
	}

	@Override
	public void onError(WebSocket socket, WebSocketException ex) {
		log.error("Error sending data to client.", ex);
	}

	@Override
	public void sendMessage(Message message) throws IOException {
		// Send the message to the given account id.
		final String msgStr = mapper.writeValueAsString(message);
		WebSocket socket = cache.get(message.getAccountId()).getSocket();
		socket.write(msgStr);
		log.trace("MSG send: {}", message.toString());
	}

	@Override
	public boolean isConnected(int accountId) {
		return cache.containsAccountId(accountId);
	}


	@Override
	public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {
		// no op.
	}

	@Override
	public int getConnectedPlayers() {
		return cache.size();
	}

}

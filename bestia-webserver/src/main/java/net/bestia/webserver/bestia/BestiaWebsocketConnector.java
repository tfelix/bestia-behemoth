package net.bestia.webserver.bestia;

import java.io.IOException;
import java.util.Hashtable;

import net.bestia.core.BestiaZoneserver;
import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.connection.ConnectionState;
import net.bestia.core.message.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class holds the references to all the player connections and enables the
 * server to communicate with the connected clients. It is a singelton so the newly
 * created websockets can attach themselves to this connector and the server can
 * send messages to the clients.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public final class BestiaWebsocketConnector implements BestiaConnectionInterface {
	
	private final static Logger log = LogManager.getLogger(BestiaWebsocketConnector.class);
	
	private Hashtable<Integer, BestiaSocket> users = new Hashtable<Integer, BestiaSocket>();
	private Hashtable<String, BestiaSocket> pendingConnections = new Hashtable<String, BestiaSocket>();
	
	private static BestiaWebsocketConnector INSTANCE;
	
	private BestiaZoneserver gameServer;
	
	private BestiaWebsocketConnector() {
		// no op.
	}
	
	public static synchronized BestiaWebsocketConnector getInstance() {
		if(INSTANCE == null) {
			INSTANCE = new BestiaWebsocketConnector();
		}
		return INSTANCE;
	}
	
	/**
	 * Sets the bestia game server. This must be done befor ANY message is send to the server.
	 * Otherwise the messages will be lost and not be processed.
	 * @param server
	 */
	public void setBestiaServer(BestiaZoneserver server) {
		gameServer = server;
	}
	
	public BestiaZoneserver getZoneserver() {
		return gameServer;
	}

	
	/* (non-Javadoc)
	 * @see net.bestia.webserver.bestia.BestiaConnection#sendMessage(net.bestia.message.Message)
	 */
	@Override
	public synchronized void sendMessage(Message msg) {
		BestiaSocket connection = users.get(msg.getAccountId());
		if(connection == null) {
			// No connection to the given user.
			log.debug("No connection to the user id: {}", msg.getAccountId());
			return;
		}			
		try {
			connection.sendMessage(msg);
		} catch (IOException e) {
			log.error("Could not send message. Removing user with id: {}.", connection.getAccountId());
			removeConnection(connection);
		}
	}

	/**
	 * Adds a new connection to the connector. This message is always
	 * 
	 * @param user
	 */
	public synchronized void addConnection(String uuid, BestiaSocket user) {
		user.setState(ConnectionState.NEW);
		pendingConnections.put(uuid.toString(), user);
	}

	/**
	 * Removes a connection from the connector.
	 * 
	 * @param user
	 */
	public synchronized void removeConnection(BestiaSocket user) {
		users.remove(user);
	}


	@Override
	public synchronized void elevateConnection(String uuid, int accountId) {
		BestiaSocket socket = pendingConnections.get(uuid);
		if(socket == null) {
			throw new IllegalArgumentException("Unknown UUID. No socket found.");
		}
		// TODO hier muss gegebennenfalls pendingConnections gelockt werden bevor 
		// andere Threads dieses wieder lesen.
		socket.setState(ConnectionState.ELEVATED);
		pendingConnections.remove(uuid);
		socket.setAccountId(accountId);
		users.put(accountId, socket);
	}

	@Override
	public boolean isConnected(int accountId) {
		// TODO Auto-generated method stub
		return false;
	}
}

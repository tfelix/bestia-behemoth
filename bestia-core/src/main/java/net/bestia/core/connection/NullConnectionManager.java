package net.bestia.core.connection;

import java.util.UUID;

import net.bestia.core.message.Message;

/**
 * ConnectionManager for testing purposes. It simply does nothing
 * when a new connection is requested from the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NullConnectionManager implements BestiaConnectionInterface {

	@Override
	public void sendMessage(Message message) {
		// no op.
	}

	@Override
	public boolean isConnected(int accountId) {
		return true;
	}

	@Override
	public int getConnectedPlayers() {
		return 0;
	}

}

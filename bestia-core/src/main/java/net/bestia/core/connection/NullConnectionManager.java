package net.bestia.core.connection;

import net.bestia.core.message.Message;

/**
 * ConnectionManager for testing purposes. It simply does nothing
 * when a new connection is requested from the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class NullConnectionManager implements BestiaConnectionManager {

	@Override
	public void sendMessage(Message message) {
		// no op.
	}

	@Override
	public void elevateConnection(String uuid, int accountId) {
		// no op.
	}

	@Override
	public void handleMessage(Message message) {
		// no op.
	}

}

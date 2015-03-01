package net.bestia.core.connection;

import java.util.Date;
import java.util.UUID;

/**
 * A very loose representation of a connection to the bestia server. In order to
 * communicate with it the external server must register the connection to the
 * server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Connection {
	
	private final Date connectionStarted;
	private final String token;
	private final int accountId;
	
	public Connection(String token, int accountId) {
		this.token = token;
		this.accountId = accountId;
		this.connectionStarted = new Date();
	}

	public int getAccountId() {
		return accountId;
	}
	
	

}

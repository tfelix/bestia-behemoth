package net.bestia.zoneserver.service;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ISet;

/**
 * This service is for managing the connections to the zone server. We must keep
 * track on which frontend which user is connected to to send directed messages
 * to it. We also keep track of all players from a given webserver if this
 * webserver goes offline we can desapwn all the player resources on the server.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ConnectionService {

	private final static Logger LOG = LoggerFactory.getLogger(ConnectionService.class);

	private final ISet<Long> connectedClients;

	public ConnectionService(HazelcastInstance hz) {

		this.connectedClients = hz.getSet("connection.clients");

	}

	/**
	 * Checks if a given account is already online.
	 * 
	 * @param accId
	 *            The account to check if it is online and connected.
	 * @return TRUE if account is currently online or FALSE.
	 */
	public boolean isConnected(long accId) {
		return connectedClients.contains(accId);
	}

	/**
	 * Registers the user account into the currently connected user number.
	 * 
	 * @param accountId
	 *            The connected account id.
	 */
	public void connected(long accountId) {
		LOG.debug("Account {} now listed as connected.", accountId);
		connectedClients.add(accountId);
	}

	public void disconnected(long accountId) {
		LOG.debug("Account {} now listed as disconnected.", accountId);
		connectedClients.remove(accountId);
	}

	public Iterator<Long> getAllConnectedAccountIds() {
		return connectedClients.iterator();
	}
}

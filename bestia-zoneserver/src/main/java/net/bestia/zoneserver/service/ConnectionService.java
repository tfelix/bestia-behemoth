package net.bestia.zoneserver.service;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

import akka.actor.Address;

/**
 * This service is for managing the connections to the zone server. We must keep
 * track on which frontend which user is connected. If this webserver goes
 * offline we can despawn all the player resources on the server.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ConnectionService {

	private final static Logger LOG = LoggerFactory.getLogger(ConnectionService.class);

	private final MultiMap<String, Long> connectedClients;
	private final IMap<Long, String> clientOnServer;

	public ConnectionService(HazelcastInstance hz) {

		this.connectedClients = hz.getMultiMap("connection.clients");
		this.clientOnServer = hz.getMap("connection.clientServer");

	}

	/**
	 * Checks if a given account is already online.
	 * 
	 * @param accId
	 *            The account to check if it is online and connected.
	 * @return TRUE if account is currently online or FALSE.
	 */
	public boolean isConnected(long accId) {
		return clientOnServer.containsValue(accId);
	}

	/**
	 * Registers the user account into the currently connected user number.
	 * 
	 * @param accountId
	 *            The connected account id.
	 */
	public void connected(long accountId, Address address) {
		if (accountId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}
		LOG.debug("Account {} now listed as connected.", accountId);

		final String addrStr = address.toString();

		connectedClients.put(addrStr, accountId);
		clientOnServer.put(accountId, addrStr);
	}

	/**
	 * Removes the single account from the connection.
	 * 
	 * @param accountId
	 */
	public void disconnectAccount(long accountId) {
		LOG.trace("Removing connection of {}.", accountId);

		final String server = clientOnServer.get(accountId);
		if (server == null) {
			return;
		}
		connectedClients.remove(server, accountId);
		clientOnServer.remove(accountId);
	}

	public void disconnectedAllFromServer(Address address) {
		LOG.debug("Removing all players from: {}", address);

		final String addStr = address.toString();

		connectedClients.lock(addStr);
		try {
			getAllConnectedAccountFromServer(address).forEachRemaining(this::disconnectAccount);
			connectedClients.remove(addStr);
		} finally {
			connectedClients.unlock(addStr);
		}
	}

	/**
	 * Returns a iterator over all connected account ids.
	 * 
	 * @return The account id.
	 */
	public Iterator<Long> getAllConnectedAccountFromServer(Address address) {
		LOG.trace("Returning all account ids connected to {}.", address);
		return connectedClients.get(address.toString()).iterator();
	}

	/**
	 * Returns an iterator over ALL connected account ids on all servers.
	 * 
	 * @return
	 */
	public Iterator<Long> getAllConnectedAccountIds() {
		return clientOnServer.keySet().iterator();
	}
}

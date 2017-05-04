package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

import akka.actor.ActorPath;
import akka.actor.Address;

/**
 * This service is for managing the connections to the zone server. We must keep
 * track on which frontend which user is connected to to send directed messages
 * to it. We also keep track of all players from a given webserver if this
 * webserver goes offline we can desapwn all the player resources on the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ConnectionService {

	private final static Logger LOG = LoggerFactory.getLogger(ConnectionService.class);
	private final IMap<Long, ActorPath> connections;
	private final MultiMap<String, Long> webserverCache;

	public ConnectionService(HazelcastInstance hz) {

		this.connections = hz.getMap("clients.connections");
		this.webserverCache = hz.getMultiMap("cache.webserver");

	}

	/**
	 * Adds a client to the cache for the given actor path.
	 * 
	 * @param accId
	 * @param path
	 */
	public void addClient(long accId, ActorPath path) {
		if (accId < 0) {
			throw new IllegalArgumentException("Account ID must be positive.");
		}

		LOG.trace("Adding client id: {} connection: {}", accId, path);
		connections.set(accId, path);
		webserverCache.put(path.address().toString(), accId);
	}

	/**
	 * Gets all clients which are connected to this address.
	 * 
	 * @param addr
	 *            The address of the webserver.
	 * @return All clients which are connected to this webserver.
	 */
	public Collection<Long> getClients(Address addr) {
		Objects.requireNonNull(addr);
		return webserverCache.get(addr.toString());
	}

	/**
	 * Removes a client from the caches.
	 * 
	 * @param accId
	 *            Account id to remove.
	 */
	public void removeClient(long accId) {
		LOG.trace("Removing client id: {} connection.", accId);
		final ActorPath ref = connections.get(accId);

		if (ref == null) {
			// This might happen if this client is actually not connected.
			return;
		}

		connections.remove(accId);
		webserverCache.remove(ref.address().toString(), accId);
	}

	/**
	 * Removes all clients connected to this address.
	 * 
	 * @param addr
	 *            The address to remove all clients.
	 */
	public void removeClients(Address addr) {
		getClients(addr).forEach(id -> removeClient(id));
	}

	/**
	 * Gets the webserver actor path to the client.
	 * 
	 * @param accId
	 *            The account id of the client.
	 * @return The path to the webserver who holds the connection. Or NULL if no
	 *         suitable connection is found.
	 */
	public ActorPath getPath(long accId) {
		return connections.get(accId);
	}
}

package net.bestia.zoneserver.service;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.MultiMap;

import akka.actor.ActorPath;
import akka.actor.Address;
import net.bestia.zoneserver.configuration.CacheConfiguration;

/**
 * This service is for managing the connections to the zone server. We must have
 * several possibilities to add and remove client connection from this system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class ConnectionService {

	private final CacheManager<Long, ActorPath> clientCache;
	private final MultiMap<String, Long> webserverCache;

	public ConnectionService(
			@Qualifier(CacheConfiguration.CLIENT_CACHE) CacheManager<Long, ActorPath> clientCache,
			HazelcastInstance hz) {
		
		this.clientCache = clientCache;
		this.webserverCache = hz.getMultiMap("cache.webserver");

	}

	public void addClient(long accId, ActorPath path) {
		clientCache.set(accId, path);
		webserverCache.put(path.address().toString(), accId);
	}
	
	public Collection<Long> getClients(Address addr) {
		return webserverCache.get(addr.toString());
	}

	public void removeClient(long accId) {
		final ActorPath ref = clientCache.get(accId);
		clientCache.remove(accId);
		webserverCache.remove(ref.address().toString(), accId);
	}

	public void removeClient(Address addr) {
		getClients(addr).forEach(id -> removeClient(id));
	}

	public ActorPath getPath(long accId) {
		return clientCache.get(accId);
	}
}

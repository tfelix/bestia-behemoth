package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorPath;
import net.bestia.zoneserver.service.CacheManager;

/**
 * This class sets up all the caches which are used to hold various objects
 * during runtime. In most cases the caches are implemented by using the generic
 * {@link CacheManager} class.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Configuration
public class CacheConfiguration {

	public final static String CLIENT_CACHE = "clientCache";

	private HazelcastInstance cache;

	@Autowired
	public void setCache(HazelcastInstance cache) {
		this.cache = cache;
	}

	/**
	 * 
	 * @return
	 */
	@Bean(name = CLIENT_CACHE)
	public CacheManager<Long, ActorPath> getClientCache() {

		return new CacheManager<>("cache.client", cache);
	}
}

package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.zone.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.zone.entity.VisibleEntity;

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

	public final static String ENTITY_CACHE = "entityCache";
	public final static String CLIENT_CACHE = "clientCache";
	public final static String ACTIVE_BESTIA_CACHE = "bestia.active";
	public final static String PLAYER_BESTIA_CACHE = "bestia.playerbestia";

	private HazelcastInstance cache;

	@Autowired
	public void setCache(HazelcastInstance cache) {
		this.cache = cache;
	}

	@Bean(name = ENTITY_CACHE)
	public CacheManager<Long, VisibleEntity> getEntityCache() {

		return new CacheManager<>("cache.entity", cache);
	}

	@Bean(name = CLIENT_CACHE)
	public CacheManager<Long, VisibleEntity> getClientCache() {

		return new CacheManager<>("cache.client", cache);
	}

	@Bean(name = ACTIVE_BESTIA_CACHE)
	public CacheManager<Long, Integer> getActiveBestiaCache() {

		return new CacheManager<>("cache.activeBestia", cache);
	}

	/**
	 * Returns the cache holding the {@link PlayerBestiaEntity}s of the players.
	 * 
	 * @return Cache of the {@link PlayerBestiaEntity}s.
	 */
	@Bean(name = PLAYER_BESTIA_CACHE)
	public CacheManager<Integer, PlayerBestiaEntity> getPlayerBestiaCache() {

		return new CacheManager<>("cache.playerBestia", cache);
	}
}

package net.bestia.zoneserver.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.zone.entity.Entity;

@Configuration
public class CachesConfiguration {
	
	public final static String ENTITY_CACHE = "entityCache";
	public final static String CLIENT_CACHE = "clientCache";
	
	private HazelcastInstance cache;
	
	@Autowired
	public void setCache(HazelcastInstance cache) {
		this.cache = cache;
	}
	
	@Bean(name=ENTITY_CACHE)
	public CacheManager<Long, Entity> getEntityCache() {
		
		return new CacheManager<>("cache.entity", cache);
		
	}
	
	@Bean(name=CLIENT_CACHE)
	public CacheManager<Long, Entity> getClientCache() {
		
		return new CacheManager<>("cache.client", cache);
		
	}
}

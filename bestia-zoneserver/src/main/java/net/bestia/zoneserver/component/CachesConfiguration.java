package net.bestia.zoneserver.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

import net.bestia.zoneserver.service.CacheManager;
import net.bestia.zoneserver.zone.entity.Entity;

@Configuration
public final class CachesConfiguration {
	
	public final static String ENTITY_CACHE = "entityCache";
	
	private HazelcastInstance cache;
	
	@Autowired
	public void setCache(HazelcastInstance cache) {
		this.cache = cache;
	}
	
	@Bean(name=ENTITY_CACHE)
	public CacheManager<Long, Entity> getEntityCache() {
		
		return new CacheManager<>("cache.entity", cache);
		
	}
}

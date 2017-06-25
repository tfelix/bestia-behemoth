package net.bestia.memoryserver.configuration;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import net.bestia.memoryserver.persistance.ComponentMapStore;
import net.bestia.memoryserver.persistance.EntityMapStore;

@Configuration
public class HazelcastConfiguration {

	@Bean
	public HazelcastInstance hazelcastInstance(
			EntityMapStore entityMapStore,
			ComponentMapStore compMapStore) {

		final Config cfg = new ClasspathXmlConfig("hazelcast.xml");
		Map<String, MapConfig> mapConfigs = cfg.getMapConfigs();

		// Set our map storages.
		MapConfig entitiesCfg = mapConfigs.get("entities");

		MapStoreConfig mapStoreCfg = new MapStoreConfig();
		mapStoreCfg.setImplementation(entityMapStore);

		entitiesCfg.setMapStoreConfig(mapStoreCfg);

		MapConfig componentCfg = new MapConfig();

		mapStoreCfg = new MapStoreConfig();
		mapStoreCfg.setImplementation(compMapStore);

		componentCfg.setMapStoreConfig(mapStoreCfg);
		
		mapConfigs.put("components", componentCfg);

		return Hazelcast.newHazelcastInstance(cfg);
	}
}

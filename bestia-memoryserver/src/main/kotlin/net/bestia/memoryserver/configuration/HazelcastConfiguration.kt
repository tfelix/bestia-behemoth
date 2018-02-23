package net.bestia.memoryserver.configuration

import net.bestia.memoryserver.persistance.ComponentMapStore
import net.bestia.memoryserver.persistance.EntityMapStore
import com.hazelcast.config.ClasspathXmlConfig
import com.hazelcast.config.MapConfig
import com.hazelcast.config.MapStoreConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val ENTITIES_MAP_NAME = "entities"
private const val COMPONENTS_MAP_NAME = "components"

/**
 * Configures the hazelcast instance.
 *
 * @author Thomas Felix
 */
@Configuration
class HazelcastConfiguration {

  @Bean
  fun hazelcastInstance(
          entityMapStore: EntityMapStore,
          compMapStore: ComponentMapStore): HazelcastInstance {

    val cfg = ClasspathXmlConfig("hazelcast.xml")
    val mapConfigs = cfg.mapConfigs

    // Set our map storages.
    val entitiesCfg = mapConfigs[ENTITIES_MAP_NAME]!!

    var mapStoreCfg = MapStoreConfig()
    mapStoreCfg.implementation = entityMapStore
    mapStoreCfg.writeDelaySeconds = 1

    entitiesCfg.mapStoreConfig = mapStoreCfg

    mapConfigs[ENTITIES_MAP_NAME] = entitiesCfg

    val componentCfg = MapConfig()

    mapStoreCfg = MapStoreConfig()
    mapStoreCfg.implementation = compMapStore
    mapStoreCfg.writeDelaySeconds = 1

    componentCfg.mapStoreConfig = mapStoreCfg

    mapConfigs[COMPONENTS_MAP_NAME] = componentCfg

    return Hazelcast.newHazelcastInstance(cfg)
  }
}

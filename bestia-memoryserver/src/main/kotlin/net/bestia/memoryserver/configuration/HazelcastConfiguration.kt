package net.bestia.memoryserver.configuration

import com.hazelcast.config.ClasspathXmlConfig
import com.hazelcast.config.MapConfig
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
  fun hazelcastInstance(): HazelcastInstance {

    val cfg = ClasspathXmlConfig("hazelcast.xml")
    val mapConfigs = cfg.mapConfigs

    val entitiesCfg = mapConfigs[ENTITIES_MAP_NAME]!!
    mapConfigs[ENTITIES_MAP_NAME] = entitiesCfg

    val componentCfg = MapConfig()
    mapConfigs[COMPONENTS_MAP_NAME] = componentCfg

    return Hazelcast.newHazelcastInstance(cfg)
  }
}

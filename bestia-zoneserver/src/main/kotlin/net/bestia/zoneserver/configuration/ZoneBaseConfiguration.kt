package net.bestia.zoneserver.configuration

import net.bestia.zoneserver.config.StaticConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Central bean definitions for the main bestia zoneserver. Some beans require a
 * special setup. These beans are setup via this configuration here.
 *
 * @author Thomas Felix
 */
@Configuration
class ZoneBaseConfiguration {

  @Bean
  @Primary
  fun entityRecycler(config: StaticConfig): EntityCache {
    return EntityCache(config.entityBufferSize)
  }
}

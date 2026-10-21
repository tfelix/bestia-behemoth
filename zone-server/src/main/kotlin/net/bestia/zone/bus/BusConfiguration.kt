package net.bestia.zone.bus

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.bus.Directory
import net.bestia.bus.EventPublisher
import net.bestia.bus.valkey.ValkeyDirectory
import net.bestia.bus.valkey.ValkeyEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "bus", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class BusConfiguration(private val properties: BusProperties) {

  @Bean(destroyMethod = "shutdown")
  fun busClient(): RedisClient {
    require(properties.host.isNotBlank()) { "bus.host is not set; set bus.enabled=false to run without the bus" }

    val uri = RedisURI.Builder.redis(properties.host, properties.port)
      .withAuthentication(properties.username, properties.password.toCharArray())
      .build()
    return RedisClient.create(uri)
  }

  @Bean(destroyMethod = "close")
  fun busDirectory(busClient: RedisClient): Directory {
    return ValkeyDirectory(busClient)
  }

  @Bean(destroyMethod = "close")
  fun busEventPublisher(busClient: RedisClient): EventPublisher {
    return ValkeyEventPublisher(busClient)
  }
}

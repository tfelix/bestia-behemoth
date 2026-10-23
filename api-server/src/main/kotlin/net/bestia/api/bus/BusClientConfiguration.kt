package net.bestia.api.bus

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.api.metrics.BusClientMeters
import net.bestia.bus.BusKeys
import net.bestia.bus.Directory
import net.bestia.bus.contract.ZoneEvent
import net.bestia.bus.valkey.ValkeyDirectory
import net.bestia.bus.valkey.ValkeyNodeBus
import net.bestia.bus.valkey.ValkeyStreamReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The api-server's end of the bus, as the `api` user of the zone's Valkey. */
@Configuration
class BusClientConfiguration(private val properties: BusClientProperties) {

  @Bean(destroyMethod = "shutdown")
  fun busClient(): RedisClient {
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
  fun nodeBus(busClient: RedisClient, busDirectory: Directory): ValkeyNodeBus {
    return ValkeyNodeBus(busClient, busDirectory, properties.defaultNode, properties.instanceId)
  }

  @Bean(destroyMethod = "close")
  fun eventReader(busClient: RedisClient): ValkeyStreamReader<ZoneEvent> {
    return ValkeyStreamReader(busClient, BusKeys.EVENTS, ZoneEvent::class.java)
  }

  @Bean
  fun busClientMeters(nodeBus: ValkeyNodeBus, eventReader: ValkeyStreamReader<ZoneEvent>): MeterBinder {
    return BusClientMeters(nodeBus, eventReader)
  }
}

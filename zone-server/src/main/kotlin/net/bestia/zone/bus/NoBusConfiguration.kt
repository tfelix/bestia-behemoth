package net.bestia.zone.bus

import net.bestia.bus.Directory
import net.bestia.bus.EventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** `bus.enabled: false`, the test suite: events go nowhere and nobody is registered as in the world. */
@Configuration
@ConditionalOnProperty(prefix = "bus", name = ["enabled"], havingValue = "false")
class NoBusConfiguration {

  @Bean
  fun busDirectory(): Directory {
    return object : Directory {
      override fun register(accountIds: Collection<Long>, nodeId: String, ttlSeconds: Long) {}

      override fun remove(accountId: Long) {}

      override fun nodeOf(accountId: Long): String? {
        return null
      }
    }
  }

  @Bean
  fun busEventPublisher(): EventPublisher {
    return EventPublisher { true }
  }
}

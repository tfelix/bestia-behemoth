package net.bestia.zone.bus

import net.bestia.bus.WorldRebirth
import net.bestia.bus.contract.LastWorld
import net.bestia.bus.AppPresence
import net.bestia.bus.Directory
import net.bestia.bus.EventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** `bus.enabled: false`, the test suite: events go nowhere, nobody is registered as in the world, and no app is open. */
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
  fun busAppPresence(): AppPresence {
    return object : AppPresence {
      override fun markOpen(accountIds: Collection<Long>, ttlSeconds: Long) {}

      override fun isOpen(accountId: Long): Boolean {
        return false
      }
    }
  }

  @Bean
  fun busWorldRebirth(): WorldRebirth {
    return object : WorldRebirth {
      override fun begin(lastWorld: LastWorld) {}

      override fun end() {}

      override fun isUnderway(): Boolean {
        return false
      }

      override fun lastWorld(): LastWorld? {
        return null
      }
    }
  }

  @Bean
  fun busEventPublisher(): EventPublisher {
    return EventPublisher { true }
  }
}

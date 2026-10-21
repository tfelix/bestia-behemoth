package net.bestia.login.zone

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SocketOptions
import net.bestia.bus.BusKeys
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamAppender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * The login server's end of the bus: it only appends kick orders. Connecting waits for the first kick, so the
 * login server starts and signs players in while Valkey is down.
 */
@Configuration
class ZoneBusConfiguration(private val config: BusConfig) {

  @Bean(destroyMethod = "shutdown")
  fun busClient(): RedisClient {
    val timeout = Duration.ofMillis(config.commandTimeoutMillis)
    val uri = RedisURI.Builder.redis(config.host, config.port)
      .withAuthentication(config.username, config.password.toCharArray())
      .withTimeout(timeout)
      .build()

    return RedisClient.create(uri).apply {
      options = ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(timeout).build()).build()
    }
  }

  @Bean(destroyMethod = "close")
  fun kickOrders(busClient: RedisClient): ValkeyStreamAppender<KickOrder> {
    return ValkeyStreamAppender(busClient, BusKeys.KICKS, { it.accountId })
  }
}

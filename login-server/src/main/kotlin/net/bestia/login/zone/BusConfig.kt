package net.bestia.login.zone

import org.springframework.boot.context.properties.ConfigurationProperties

/** The zones' Valkey, as its `login` user, which may only append to the kick stream. */
@ConfigurationProperties(prefix = "bus")
data class BusConfig(
  val host: String,
  val port: Int = 6379,
  val username: String,
  val password: String,
  /**
   * How long a kick may wait for Valkey, to connect or to answer. It is appended in the request that caused it,
   * so an unreachable Valkey must not hold up that request for long.
   */
  val commandTimeoutMillis: Long = 2000,
)

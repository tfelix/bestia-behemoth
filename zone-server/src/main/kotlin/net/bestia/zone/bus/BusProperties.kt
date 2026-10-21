package net.bestia.zone.bus

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bus")
data class BusProperties(
  val enabled: Boolean = true,
  val host: String = "",
  val port: Int = 6379,
  val username: String = "",
  val password: String = "",
  val nodeId: String,
  val maxInFlight: Int = 64,
  val directoryTtlSeconds: Long = 30,
)

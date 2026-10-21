package net.bestia.api.bus

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bus")
data class BusClientProperties(
  val host: String,
  val port: Int = 6379,
  val username: String,
  val password: String,
  val defaultNode: String,
  val instanceId: String,
  val requestTimeoutMillis: Long = 5000,
)

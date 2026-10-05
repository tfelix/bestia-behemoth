package net.bestia.login.zone

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "zone-directory")
@ConfigurationPropertiesScan
data class ZoneDirectoryConfig(
  val zones: List<ZoneEndpoint> = emptyList(),
  /** Per call, so one zone that hangs cannot hold up the request that caused the kick for long. */
  val requestTimeoutMillis: Long = 3000
)

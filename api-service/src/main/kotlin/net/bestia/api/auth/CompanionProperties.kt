package net.bestia.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "companion")
data class CompanionProperties(
  /** login-server's `jwt.companion-secret`. */
  val jwtSecret: String,
  val requestsPerMinute: Int = 600,
  /** Pages allowed to open the WebSocket: the app's own origin, which behind the proxy is not this server's. */
  val allowedOrigins: List<String> = emptyList(),
)

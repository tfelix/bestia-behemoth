package net.bestia.login.gamelogin

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "companion-login")
data class CompanionLoginConfig(
  /** The only pages a companion login may redirect to, compared exactly. Empty: companion login is off. */
  val redirectUris: List<String> = emptyList(),

  /** Where the refresh cookie is sent. The app reaches these endpoints through its own origin, under this path. */
  val refreshCookiePath: String = "/api/v1/auth/companion",
)

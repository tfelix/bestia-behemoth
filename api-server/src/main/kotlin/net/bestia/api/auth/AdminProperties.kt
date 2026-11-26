package net.bestia.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
  /** The bearer token of `/v1/admin/`. Without one, every admin call is refused. */
  val token: String? = null,
)

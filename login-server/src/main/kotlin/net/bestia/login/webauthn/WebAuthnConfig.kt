package net.bestia.login.webauthn

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The RP ID is written into every credential the moment it is created and there is no way to
 * migrate it afterwards: change it and every passkey in the database stops resolving. Set it to the
 * registrable domain (`bestia.game`), not to the host the login page happens to live on, so the
 * page can move between subdomains later.
 */
@ConfigurationProperties(prefix = "webauthn")
data class WebAuthnConfig(
  val rpId: String,
  val rpName: String,

  /**
   * Origins the ceremony may be run from. Anything else is rejected during verification, which is
   * what stops a phishing page from replaying a challenge it obtained from us.
   */
  val origins: List<String>,

  /**
   * Relaxes origin matching to ignore the port. Only ever correct for development, where the page
   * is served from `http://localhost:8080` instead of the implicit `https://<rp-id>:443`.
   */
  val allowOriginPort: Boolean = false,

  val ceremonyTtlSeconds: Long = 300
)

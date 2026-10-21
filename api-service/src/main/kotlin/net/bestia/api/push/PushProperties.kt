package net.bestia.api.push

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "push")
data class PushProperties(
  /** The uncompressed P-256 point, base64url. Browsers subscribe against this; changing it orphans every subscription. */
  val vapidPublicKey: String,
  /** The private scalar, base64url. */
  val vapidPrivateKey: String,
  val subject: String,
  val perAccountPerHour: Int = 6,
  /**
   * Hosts a subscription may point at, matched as the host or a parent domain. A subscription is only a URL this
   * service will POST to, so without the list any signed-in player could aim it at an internal address.
   */
  val allowedEndpointHosts: List<String> = listOf(
    "fcm.googleapis.com",
    "updates.push.services.mozilla.com",
    "push.apple.com",
    "notify.windows.com",
  ),
)

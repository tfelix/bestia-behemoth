package net.bestia.zone.socket

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "socket")
@ConfigurationPropertiesScan
class SocketServerConfig(
  val ipAddress: String,
  val port: Int,
  val authenticationTimeoutSeconds: Long = 30L,
  val filterLogMessages: List<String>,
  /** Messages one connection may send in a burst before [messagesPerSecond] applies. */
  val messageBurst: Int = 200,
  val messagesPerSecond: Int = 50,
  /** Largest frame an unauthenticated connection may send; the authentication message is well under 1 KiB. */
  val maxFrameBytesBeforeAuth: Int = 8192,
  val maxFrameBytes: Int = 65536,
)
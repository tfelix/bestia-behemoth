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
  /** Concurrent connections from one IP address. Players behind one NAT share it, so not too tight. */
  val maxConnectionsPerAddress: Int = 10,
  /** A channel turns unwritable once this many outbound bytes queue up, and writable again below the low mark. */
  val writeBufferHighBytes: Int = 256 * 1024,
  val writeBufferLowBytes: Int = 64 * 1024,
  /** How long a client may stay unwritable before [SlowConsumerGuard] drops it. */
  val unwritableTimeoutSeconds: Long = 10L,
  /** Unsent bytes past which a client is dropped at once, however short it has been busy. */
  val maxWriteBacklogBytes: Long = 4L * 1024 * 1024,
  /** A connection that sends nothing for this long counts as dead; a live client pings every 10 s. */
  val readIdleTimeoutSeconds: Long = 30L,
)

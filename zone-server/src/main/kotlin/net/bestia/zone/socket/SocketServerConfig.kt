package net.bestia.zone.socket

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "socket")
@ConfigurationPropertiesScan
class SocketServerConfig(
  val ipAddress: String,
  val port: Int,
  val authenticationTimeoutSeconds: Long = 30L,
  val filterLogMessages: List<String>
)
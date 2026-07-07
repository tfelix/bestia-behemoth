package net.bestia.zone.socket

import net.bestia.zone.account.authentication.AuthenticationProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!no-socket")
class ClientMessageHandlerContext(
  val applicationEventPublisher: ApplicationEventPublisher,
  val authProcessor: AuthenticationProcessor,
  val socketConfig: SocketServerConfig,
  val channelRegistry: ChannelRegistry,
  @Value("\${zone.version}")
  val version: String,
)
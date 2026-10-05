package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.account.Authority
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.account.authentication.HttpTicketService
import net.bestia.zone.message.MessageEnvelopeReceivedEvent
import org.springframework.context.ApplicationEventPublisher

/**
 * One [ClientMessageHandler] on an [EmbeddedChannel], with any token accepted and every event recorded.
 * [onMessage] stands in for the dispatch that a received message reaches.
 */
class ClientHandlerFixture(
  private val onMessage: (MessageEnvelopeReceivedEvent) -> Unit = {},
) {

  val events = mutableListOf<Any>()

  val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))

  val channel = EmbeddedChannel(ClientMessageHandler(context()))

  fun authenticate(protocolVersion: Int = CURRENT_PROTOCOL_VERSION) {
    val authentication = AuthenticationProto.Authentication.newBuilder()
      .setToken("faked")
      .setProtocolVersion(protocolVersion)

    channel.writeInbound(EnvelopeProto.Envelope.newBuilder().setAuthentication(authentication).build())
  }

  fun disconnectReason(): String? {
    while (true) {
      val out = channel.readOutbound<EnvelopeProto.Envelope>() ?: return null
      if (out.hasDisconnected()) return out.disconnected.reason
    }
  }

  private fun context(): ClientMessageHandlerContext {
    return ClientMessageHandlerContext(
      applicationEventPublisher = ApplicationEventPublisher { event ->
        events.add(event)
        if (event is MessageEnvelopeReceivedEvent) onMessage(event)
      },
      authProcessor = object : AuthenticationProcessor {
        override fun authenticate(msg: EnvelopeProto.Envelope) =
          AuthenticationProcessor.AuthenticationSuccess(ACCOUNT_ID, setOf(Authority.MAP_MOVE))
      },
      socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList()),
      channelRegistry = registry,
      zoneReadinessService = ZoneReadinessService().apply { markReady() },
      httpTicketService = HttpTicketService(),
      inbox = { _, _, task -> java.util.concurrent.CompletableFuture.completedFuture(task()) },
      version = "test"
    )
  }

  companion object {
    const val ACCOUNT_ID = 42L
    val CURRENT_PROTOCOL_VERSION = AuthenticationProto.ProtocolVersion.PROTOCOL_VERSION_CURRENT.number
  }
}

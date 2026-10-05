package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.account.authentication.HttpTicketService
import net.bestia.zone.message.MessageEnvelopeReceivedEvent
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PingOuterClass
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every message runs a handler, most of them under the world lock, so one client sending as fast as its line
 * allows stalls the zone for everybody.
 */
class ClientMessageRateLimitTest {

  private val events = mutableListOf<Any>()

  private val context = ClientMessageHandlerContext(
    applicationEventPublisher = ApplicationEventPublisher { events.add(it) },
    authProcessor = object : AuthenticationProcessor {
      override fun authenticate(msg: EnvelopeProto.Envelope) =
        AuthenticationProcessor.AuthenticationSuccess(42L, emptySet())
    },
    socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList(), messageBurst = 5, messagesPerSecond = 1),
    channelRegistry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList())),
    zoneReadinessService = ZoneReadinessService().apply { markReady() },
    httpTicketService = HttpTicketService(),
    inbox = { _, _, task -> java.util.concurrent.CompletableFuture.completedFuture(task()) },
    version = "test"
  )

  @Test
  fun `a client sending faster than the limit is disconnected`() {
    val channel = EmbeddedChannel(ClientMessageHandler(context))

    channel.writeInbound(authentication())
    repeat(10) {
      // An EmbeddedChannel refuses writes once the server has closed it.
      if (channel.isOpen) channel.writeInbound(ping())
    }

    assertTrue(events.filterIsInstance<MessageEnvelopeReceivedEvent>().size <= 4, "at most the burst is handled")
    assertEquals("RATE_LIMITED", disconnectReasonOf(channel))
    assertFalse(channel.isActive)
  }

  private fun disconnectReasonOf(channel: EmbeddedChannel): String? {
    while (true) {
      val out = channel.readOutbound<EnvelopeProto.Envelope>() ?: return null
      if (out.hasDisconnected()) {
        return out.disconnected.reason
      }
    }
  }

  private fun authentication(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken("faked").setProtocolVersion(AuthenticationProto.ProtocolVersion.PROTOCOL_VERSION_CURRENT.number))
      .build()
  }

  private fun ping(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder().setPing(PingOuterClass.Ping.getDefaultInstance()).build()
  }
}

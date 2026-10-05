package net.bestia.zone.socket

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.ChatCmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.account.authentication.HttpTicketService
import net.bestia.zone.message.MessageEnvelopeReceivedEvent
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Every frame is buffered whole before it is decoded. Anybody can connect, so a stranger must not be able to make
 * the server buffer a megabyte per connection, and an authenticated client sends nothing near that size.
 */
class FrameSizeLimitTest {

  private val events = mutableListOf<Any>()
  private val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))

  private val context = ClientMessageHandlerContext(
    applicationEventPublisher = ApplicationEventPublisher { events.add(it) },
    authProcessor = object : AuthenticationProcessor {
      override fun authenticate(msg: EnvelopeProto.Envelope) =
        AuthenticationProcessor.AuthenticationSuccess(ACCOUNT_ID, emptySet())
    },
    socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList()),
    channelRegistry = registry,
    zoneReadinessService = ZoneReadinessService().apply { markReady() },
    httpTicketService = HttpTicketService(),
    version = "test"
  )

  @Test
  fun `an oversized frame before authentication closes the connection`() {
    val channel = EmbeddedChannel(ZoneChannelInitializer(context))

    write(channel, authentication(token = "x".repeat(16 * 1024)))

    assertFalse(channel.isOpen)
    assertNull(registry.getChannel(ACCOUNT_ID))
  }

  @Test
  fun `an authenticated client can still send a large message`() {
    val channel = EmbeddedChannel(ZoneChannelInitializer(context))
    write(channel, authentication(token = "faked"))

    write(channel, chat("x".repeat(16 * 1024)))

    assertEquals(1, events.filterIsInstance<MessageEnvelopeReceivedEvent>().size)
  }

  @Test
  fun `an oversized frame after authentication closes the connection`() {
    val channel = EmbeddedChannel(ZoneChannelInitializer(context))
    write(channel, authentication(token = "faked"))

    write(channel, chat("x".repeat(128 * 1024)))

    assertFalse(channel.isOpen)
  }

  private fun write(channel: EmbeddedChannel, envelope: EnvelopeProto.Envelope) {
    // An EmbeddedChannel refuses writes once the server has closed it, and rethrows what closed it.
    runCatching { channel.writeInbound(EnvelopeFraming.frame(ByteBufAllocator.DEFAULT, envelope)) }
  }

  private fun authentication(token: String): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken(token))
      .build()
  }

  private fun chat(text: String): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setChatCmsg(ChatCmsgProto.ChatCMSG.newBuilder().setText(text))
      .build()
  }

  private companion object {
    const val ACCOUNT_ID = 42L
  }
}

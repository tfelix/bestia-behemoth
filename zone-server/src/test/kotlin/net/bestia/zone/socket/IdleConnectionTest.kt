package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.timeout.IdleStateEvent
import net.bestia.account.Authority
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.account.authentication.AuthenticationProcessor
import net.bestia.zone.account.authentication.HttpTicketService
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Netty's own `IdleStateHandler` measures the silence; this covers what the server does about it. */
class IdleConnectionTest {

  private val events = mutableListOf<Any>()

  private val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))

  private val channel = EmbeddedChannel(ClientMessageHandler(context())).apply {
    writeInbound(authEnvelope())
  }

  @Test
  fun `a silent client is told why and closed`() {
    channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT)

    assertEquals("IDLE_TIMEOUT", disconnectReason())
    assertFalse(channel.isOpen)
  }

  @Test
  fun `closing a silent client releases its account`() {
    channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT)

    assertEquals(1, events.filterIsInstance<AccountDisconnectedEvent>().size)
  }

  @Test
  fun `a client that is only slow to receive is kept`() {
    channel.pipeline().fireUserEventTriggered(IdleStateEvent.WRITER_IDLE_STATE_EVENT)

    assertTrue(channel.isOpen)
  }

  private fun disconnectReason(): String? {
    while (true) {
      val out = channel.readOutbound<EnvelopeProto.Envelope>() ?: return null
      if (out.hasDisconnected()) return out.disconnected.reason
    }
  }

  private fun context(): ClientMessageHandlerContext {
    return ClientMessageHandlerContext(
      applicationEventPublisher = ApplicationEventPublisher { events.add(it) },
      authProcessor = object : AuthenticationProcessor {
        override fun authenticate(msg: EnvelopeProto.Envelope) =
          AuthenticationProcessor.AuthenticationSuccess(ACCOUNT_ID, setOf(Authority.MAP_MOVE))
      },
      socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList()),
      channelRegistry = registry,
      zoneReadinessService = ZoneReadinessService().apply { markReady() },
      httpTicketService = HttpTicketService(),
      version = "test"
    )
  }

  private fun authEnvelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken("faked"))
      .build()
  }

  private companion object {
    const val ACCOUNT_ID = 42L
  }
}

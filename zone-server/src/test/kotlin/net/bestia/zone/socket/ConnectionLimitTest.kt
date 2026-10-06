package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.session.AuthenticationProcessor
import net.bestia.zone.session.HttpTicketService
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every open connection costs a handler, buffers and an authentication timer, so one address cannot hold an
 * unbounded number of them. Every EmbeddedChannel reports the same remote address, so these all count as one.
 */
class ConnectionLimitTest {

  private val initializer = ZoneChannelInitializer(
    ClientMessageHandlerContext(
      applicationEventPublisher = ApplicationEventPublisher { },
      authProcessor = object : AuthenticationProcessor {
        override fun authenticate(msg: EnvelopeProto.Envelope) = AuthenticationProcessor.AuthenticationFailed
      },
      socketConfig = SocketServerConfig("127.0.0.1", 0, 30L, emptyList(), maxConnectionsPerAddress = 2),
      channelRegistry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList())),
      zoneReadinessService = ZoneReadinessService().apply { markReady() },
      httpTicketService = HttpTicketService(),
      inbox = object : net.bestia.zone.message.AccountTaskExecutor {
        override fun onTick(accountId: net.bestia.zone.util.AccountId, task: net.bestia.zone.ecs.core.World.() -> Unit) =
          error("connection events run on IO")

        override fun onIo(accountId: net.bestia.zone.util.AccountId, task: () -> Unit) =
          java.util.concurrent.CompletableFuture.completedFuture(task())
      },
      version = "test"
    )
  )

  @Test
  fun `connections from one address beyond the cap are closed`() {
    val first = EmbeddedChannel(initializer)
    val second = EmbeddedChannel(initializer)
    val third = EmbeddedChannel(initializer)

    assertTrue(first.isActive)
    assertTrue(second.isActive)
    assertFalse(third.isActive)
  }

  @Test
  fun `a closed connection frees its place`() {
    val first = EmbeddedChannel(initializer)
    EmbeddedChannel(initializer)

    first.close()

    assertTrue(EmbeddedChannel(initializer).isActive)
  }
}

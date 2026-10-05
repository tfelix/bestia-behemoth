package net.bestia.zone.socket

import io.netty.handler.timeout.IdleStateEvent
import net.bestia.zone.account.AccountDisconnectedEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Netty's own `IdleStateHandler` measures the silence; this covers what the server does about it. */
class IdleConnectionTest {

  private val client = ClientHandlerFixture().apply { authenticate() }

  @Test
  fun `a silent client is told why and closed`() {
    client.channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT)

    assertEquals("IDLE_TIMEOUT", client.disconnectReason())
    assertFalse(client.channel.isOpen)
  }

  @Test
  fun `closing a silent client releases its account`() {
    client.channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT)

    assertEquals(1, client.events.filterIsInstance<AccountDisconnectedEvent>().size)
  }

  @Test
  fun `a client that is only slow to receive is kept`() {
    client.channel.pipeline().fireUserEventTriggered(IdleStateEvent.WRITER_IDLE_STATE_EVENT)

    assertTrue(client.channel.isOpen)
  }
}

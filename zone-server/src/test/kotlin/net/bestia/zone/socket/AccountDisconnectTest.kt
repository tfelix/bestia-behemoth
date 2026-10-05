package net.bestia.zone.socket

import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.EnvelopeProto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** A kick or a ban has to end the connection of an account that is already in the game. */
class AccountDisconnectTest {

  private val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))

  @Test
  fun `a disconnected account is told why and its connection closes`() {
    val channel = EmbeddedChannel()
    registry.registerChannel(ACCOUNT, channel)

    registry.disconnect(ACCOUNT, "BANNED")

    assertEquals("BANNED", channel.readOutbound<EnvelopeProto.Envelope>()?.disconnected?.reason)
    assertFalse(channel.isOpen)
  }

  /** The close waits for the notice to be written, and nothing the client sends meanwhile may still act. */
  @Test
  fun `a disconnected connection reads nothing more`() {
    val channel = EmbeddedChannel()
    registry.registerChannel(ACCOUNT, channel)

    registry.disconnect(ACCOUNT, "BANNED")

    assertFalse(channel.config().isAutoRead)
  }

  @Test
  fun `an account without a connection here is not disconnected`() {
    assertFalse(registry.disconnect(ACCOUNT, "BANNED"))
  }

  private companion object {
    const val ACCOUNT = 1L
  }
}

package net.bestia.zone.socket

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tick's updates for one client leave as one flush.
 *
 * Worth its own test for the same reason as [NettyChunkFanOutTest]: swapping the batch back for a loop over
 * `sendMessage` still delivers exactly the right bytes and breaks no other test. The only symptom is a
 * separate event-loop task, flush and write syscall per component update - invisible until a crowded area
 * makes it a problem.
 */
class ChannelRegistryTest {

  private class FlushCounter : ChannelOutboundHandlerAdapter() {
    var flushes = 0
      private set

    override fun flush(ctx: ChannelHandlerContext) {
      flushes++
      ctx.flush()
    }
  }

  private object EmptySMSG : SMSG {
    override fun toBnetEnvelope(): EnvelopeProto.Envelope {
      return EnvelopeProto.Envelope.newBuilder().build()
    }
  }

  private class Client {
    val flushes = FlushCounter()
    val channel = EmbeddedChannel(flushes)
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList())).also {
      it.registerChannel(ACCOUNT_ID, channel)
    }
  }

  @Test
  fun `a batch of messages is many writes and one flush`() {
    val client = Client()

    client.registry.sendMessages(ACCOUNT_ID, listOf(EmptySMSG, EmptySMSG, EmptySMSG))

    assertEquals(3, client.channel.outboundMessages().size, "every message still goes out")
    assertEquals(1, client.flushes.flushes, "but they share one flush")
  }

  @Test
  fun `a lone message flushes on its own, so nothing waits for company`() {
    val client = Client()

    client.registry.sendMessage(ACCOUNT_ID, EmptySMSG)

    assertEquals(1, client.channel.outboundMessages().size)
    assertEquals(1, client.flushes.flushes)
  }

  @Test
  fun `an empty batch never reaches the channel`() {
    val client = Client()

    client.registry.sendMessages(ACCOUNT_ID, emptyList())

    assertTrue(client.channel.outboundMessages().isEmpty())
    assertEquals(0, client.flushes.flushes)
  }

  companion object {
    private const val ACCOUNT_ID = 1L
  }
}

package net.bestia.zone.socket

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.PingOuterClass
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the zone counts about the messages it sends and the connections it ends. */
class SocketTrafficMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val traffic = SocketTraffic(meters)
  private val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()), traffic)

  private val pong = object : SMSG {
    override fun toBnetEnvelope(): EnvelopeProto.Envelope {
      return EnvelopeProto.Envelope.newBuilder().setPong(PingOuterClass.Pong.getDefaultInstance()).build()
    }
  }

  private fun sent(type: MessageCase): Double {
    return meters.get("zone.smsg.sent").tag("type", type.name).counter().count()
  }

  private fun bytes(type: MessageCase): Double {
    return meters.get("zone.smsg.bytes").tag("type", type.name).counter().count()
  }

  private fun disconnects(reason: String): Double {
    return meters.get("zone.socket.disconnects").tag("reason", reason).counter().count()
  }

  @Test
  fun `a message sent to one client is counted with its framed size`() {
    val channel = EmbeddedChannel().also { registry.registerChannel(1L, it) }

    registry.sendMessage(1L, pong)

    assertEquals(1.0, sent(MessageCase.PONG))
    assertEquals(EnvelopeFraming.frameSize(pong.toBnetEnvelope()).toDouble(), bytes(MessageCase.PONG))
    channel.finishAndReleaseAll()
  }

  @Test
  fun `a broadcast is counted once per receiver that got it`() {
    val channels = (1L..3L).associateWith { EmbeddedChannel().also { c -> registry.registerChannel(it, c) } }

    registry.broadcast(listOf(1L, 2L, 3L, 99L), pong)

    assertEquals(3.0, sent(MessageCase.PONG), "account 99 has no connection")
    val frame = channels.getValue(1L).readOutbound<ByteBuf>()
    assertEquals(frame.readableBytes() * 3.0, bytes(MessageCase.PONG))
    frame.release()
  }

  @Test
  fun `a message type the server never sends is not counted and does not throw`() {
    traffic.sent(MessageCase.PING, 10)

    assertTrue(meters.find("zone.smsg.sent").tag("type", MessageCase.PING.name).counter() == null)
  }

  @Test
  fun `a disconnect reason is counted under its own name, with an error code folded away`() {
    traffic.disconnected("RATE_LIMITED")
    traffic.disconnected("INTERNAL_SERVER_ERROR:3f2a-uuid")
    traffic.disconnected("INTERNAL_SERVER_ERROR:another-uuid")
    traffic.disconnected("BANNED")

    assertEquals(1.0, disconnects("RATE_LIMITED"))
    assertEquals(2.0, disconnects("INTERNAL_SERVER_ERROR"))
    assertEquals(1.0, disconnects("BANNED"))
  }

  @Test
  fun `a reason nobody listed becomes OTHER rather than a series of its own`() {
    traffic.disconnected("a free text a gm typed")

    assertEquals(1.0, disconnects("OTHER"))
  }

  @Test
  fun `a refused connection is counted`() {
    val limit = ConnectionLimitHandler(maxPerAddress = 1, traffic = traffic)
    val first = EmbeddedChannel(limit)
    val second = EmbeddedChannel(limit)

    assertTrue(first.isActive)
    assertTrue(!second.isActive)
    assertEquals(1.0, meters.get("zone.socket.refused").tag("reason", "per_address").counter().count())
  }
}

package net.bestia.zone.socket

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TrafficCounterTest {

  private val meters = SimpleMeterRegistry()
  private val channel = EmbeddedChannel(TrafficCounter(SocketTraffic(meters)))

  @Test
  fun `writes count when they are flushed, one sample per flush`() {
    channel.write(Unpooled.wrappedBuffer(ByteArray(100)))
    channel.write(Unpooled.wrappedBuffer(ByteArray(50)))
    channel.flush()

    assertEquals(150.0, meters.get("zone.socket.written").counter().count())
    assertEquals(1.0, meters.get("zone.socket.flushes").counter().count())
    val perFlush = meters.get("zone.socket.flush.size").summary()
    assertEquals(1L, perFlush.count())
    assertEquals(150.0, perFlush.totalAmount())

    channel.finishAndReleaseAll()
  }

  @Test
  fun `reads are counted`() {
    channel.writeInbound(Unpooled.wrappedBuffer(ByteArray(30)))

    assertEquals(30.0, meters.get("zone.socket.read").counter().count())

    channel.finishAndReleaseAll()
  }

  @Test
  fun `an open connection counts until it closes`() {
    assertEquals(1.0, meters.get("zone.socket.channels").gauge().value())

    channel.close()

    assertEquals(0.0, meters.get("zone.socket.channels").gauge().value())
  }
}

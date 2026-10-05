package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PingOuterClass
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ChannelRegistryBroadcastTest {

  private val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))

  private val channels = (1L..5L).associateWith { EmbeddedChannel() }.onEach { (accountId, channel) ->
    registry.registerChannel(accountId, channel)
  }

  private var serialisations = 0

  private val message = object : SMSG {
    override fun toBnetEnvelope(): EnvelopeProto.Envelope {
      serialisations++

      return EnvelopeProto.Envelope.newBuilder().setPong(PingOuterClass.Pong.getDefaultInstance()).build()
    }
  }

  @Test
  fun `a broadcast is serialised once for all its recipients`() {
    registry.broadcast(channels.keys, message)

    assertEquals(1, serialisations)
  }

  @Test
  fun `every recipient gets the same frame`() {
    registry.broadcast(channels.keys, message)

    val frames = channels.values.map { channel ->
      val frame = channel.readOutbound<ByteBuf>()
      val bytes = ByteArray(frame.readableBytes()).also { frame.readBytes(it) }
      frame.release()
      bytes.toList()
    }

    assertEquals(1, frames.distinct().size)
  }
}

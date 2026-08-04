package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.stream.ChunkPatchCodec
import net.bestia.zone.world.stream.ChunkPatchSMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The claim the whole change-broadcast design rests on: one message reaching many clients is serialised once.
 *
 * Worth a test of its own rather than trusting the implementation, because the failure mode is invisible. If
 * somebody replaces the fan-out with a loop over `sendMessage`, every client still receives exactly the right
 * bytes and every other test still passes - the only symptom is thirty times the CPU and thirty times the
 * garbage per edit in a crowded area, which is precisely the situation nobody profiles until it is a problem.
 */
class NettyChunkFanOutTest {

  /** Counts how many times its delegate is asked to produce an envelope. */
  private class CountingSMSG(private val delegate: SMSG) : SMSG {
    var serialisations = 0
      private set

    override fun toBnetEnvelope(): EnvelopeProto.Envelope {
      serialisations++
      return delegate.toBnetEnvelope()
    }
  }

  private fun registryWith(channels: Map<Long, EmbeddedChannel>): ChannelRegistry {
    val registry = ChannelRegistry(SocketServerConfig("127.0.0.1", 0, 30L, emptyList()))
    channels.forEach { (accountId, channel) -> registry.registerChannel(accountId, channel) }
    return registry
  }

  /**
   * The real outbound pipeline, in the order `SocketServer` builds it.
   *
   * Included on purpose. The reason a pre-framed buffer can be written at all is that neither outbound
   * encoder matches a raw `ByteBuf` - `BigEndianLengthFieldPrepender` takes an `Envelope` and
   * `ProtobufEncoder` a `MessageLite` - so it passes through both untouched. That is a property of two
   * third-party base classes, not of any code here, and a test that skipped the pipeline would not be
   * testing the thing that could break.
   */
  private fun pipelineChannel() = EmbeddedChannel(
    LengthFieldBasedFrameDecoder(1_048_576, 0, 4, 0, 4),
    ProtobufDecoder(EnvelopeProto.Envelope.getDefaultInstance()),
    ProtobufEncoder(),
    BigEndianLengthFieldPrepender()
  )

  private fun patch() = ChunkPatchSMSG.of(
    chunk = ChunkPos(3, -4, 0),
    fromRevision = 0,
    toRevision = 1,
    removals = IntArray(10) { ChunkDelta.pack(it * 977, Occupancy.EMPTY) }
  )

  @Test
  fun `one message to thirty clients is serialised exactly once`() {
    val channels = (1L..30L).associateWith { bareChannel() }
    val fanOut = NettyChunkFanOut(registryWith(channels))

    val message = CountingSMSG(patch())
    val written = fanOut.fanOut(channels.keys, message)

    assertEquals(30, written, "every registered client should have been written to")
    assertEquals(
      1, message.serialisations,
      "the envelope must be built once for the whole fan-out, not once per recipient"
    )
  }

  @Test
  fun `every client receives the identical framed bytes`() {
    val channels = (1L..30L).associateWith { pipelineChannel() }
    val fanOut = NettyChunkFanOut(registryWith(channels))

    val source = patch()
    fanOut.fanOut(channels.keys, source)

    val expected = EnvelopeFraming.frame(io.netty.buffer.ByteBufAllocator.DEFAULT, source.toBnetEnvelope())
    val expectedBytes = ByteArray(expected.readableBytes()).also { expected.readBytes(it) }
    expected.release()

    channels.values.forEach { channel ->
      channel.flushOutbound()

      val out = channel.readOutbound<ByteBuf>()
      val actual = ByteArray(out.readableBytes()).also { out.readBytes(it) }
      out.release()

      assertTrue(actual.contentEquals(expectedBytes), "a recipient received different bytes")
    }
  }

  @Test
  fun `the framed payload decodes back to the same message`() {
    // Feeding the outbound frame straight back into an inbound pipeline is the cheapest possible proof that
    // the length prefix and the payload agree - which is exactly what a hand-rolled frame gets wrong.
    val sender = pipelineChannel()
    val registry = registryWith(mapOf(1L to sender))
    val source = patch()

    NettyChunkFanOut(registry).fanOut(listOf(1L), source)
    sender.flushOutbound()

    val frame = sender.readOutbound<ByteBuf>()
    val receiver = pipelineChannel()
    receiver.writeInbound(frame)

    val envelope = receiver.readInbound<EnvelopeProto.Envelope>()
    assertTrue(envelope.hasChunkPatch(), "the frame did not decode as a chunk patch")

    val patch = envelope.chunkPatch
    assertEquals(3, patch.pos.x)
    assertEquals(-4, patch.pos.y, "a negative coordinate must survive the wire; sint32 exists for this")
    assertEquals(1, patch.toRevision)
    assertEquals(10, ChunkPatchCodec.decode(patch.removals.toByteArray()).size)
    assertEquals(10, patch.removalCount, "the count is carried rather than divided out of the byte length")
  }

  @Test
  fun `an unwritable or dead channel is skipped rather than queued`() {
    val live = bareChannel()
    val dead = bareChannel().also { it.close().sync() }

    val fanOut = NettyChunkFanOut(registryWith(mapOf(1L to live, 2L to dead)))

    // A skipped recipient must be reported as not written, because the caller uses that to decide whether to
    // record the client as holding the chunk - and a client recorded as holding terrain it never received
    // would go on receiving patches it cannot apply.
    assertEquals(1, fanOut.fanOut(listOf(1L, 2L), patch()))
  }

  /** An `EmbeddedChannel` with no handlers, for the cases that only care about what was written. */
  private fun bareChannel() = EmbeddedChannel()
}

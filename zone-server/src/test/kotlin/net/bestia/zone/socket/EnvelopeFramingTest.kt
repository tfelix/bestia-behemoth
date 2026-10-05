package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.protobuf.ProtobufDecoder
import net.bestia.bnet.proto.ChatSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeFramingTest {

  private val envelope = EnvelopeProto.Envelope.newBuilder()
    .setChatSmsg(ChatSmsgProto.ChatSMSG.newBuilder().setText("x".repeat(5000)))
    .build()

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `a frame is the length followed by the serialised envelope`(direct: Boolean) {
    val frame = EnvelopeFraming.frame(UnpooledByteBufAllocator(direct), envelope)

    assertEquals(envelope.serializedSize, frame.readInt())
    assertTrue(bytesOf(frame).contentEquals(envelope.toByteArray()))
  }

  @Test
  fun `an envelope written to the pipeline decodes back to the same envelope`() {
    val sender = EmbeddedChannel(BigEndianLengthFieldPrepender())
    sender.writeOutbound(envelope)

    val receiver = EmbeddedChannel(
      LengthFieldBasedFrameDecoder(1_048_576, 0, 4, 0, 4),
      ProtobufDecoder(EnvelopeProto.Envelope.getDefaultInstance())
    )
    receiver.writeInbound(sender.readOutbound<ByteBuf>())

    assertEquals(envelope, receiver.readInbound())
  }

  private fun bytesOf(buffer: ByteBuf): ByteArray {
    val bytes = ByteArray(buffer.readableBytes())
    buffer.readBytes(bytes)
    buffer.release()

    return bytes
  }
}

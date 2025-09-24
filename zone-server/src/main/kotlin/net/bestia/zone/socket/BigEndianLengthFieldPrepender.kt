package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import net.bestia.bnet.proto.EnvelopeProto
import java.nio.ByteOrder

class BigEndianLengthFieldPrepender : MessageToByteEncoder<EnvelopeProto.Envelope>() {

  override fun encode(ctx: ChannelHandlerContext, msg: EnvelopeProto.Envelope, out: ByteBuf) {
    // Serialize the protobuf message to a byte array
    val messageBytes = msg.toByteArray()

    // Create a byte buffer with big-endian order
    val buffer = Unpooled.buffer(4 + messageBytes.size).order(ByteOrder.BIG_ENDIAN)

    // Put the size prefix (4 bytes)
    buffer.writeInt(messageBytes.size)

    // Put the message bytes
    buffer.writeBytes(messageBytes)

    // Write the buffer to the output
    out.writeBytes(buffer)
  }
}
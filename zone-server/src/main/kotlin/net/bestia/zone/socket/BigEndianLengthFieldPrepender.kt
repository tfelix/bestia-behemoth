package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import net.bestia.bnet.proto.EnvelopeProto

/**
 * Serialises an [EnvelopeProto.Envelope] and prefixes its length.
 *
 * Being a `MessageToByteEncoder<Envelope>` rather than a generic one matters: anything written to the
 * channel that is not an `Envelope` - notably an already-framed `ByteBuf` from [ChunkFanOut] - does not
 * match, so it is passed along untouched and reaches the socket as it is. That is what lets a chunk payload
 * be serialised once for many recipients without a second outbound pipeline.
 */
class BigEndianLengthFieldPrepender : MessageToByteEncoder<EnvelopeProto.Envelope>() {

  override fun encode(ctx: ChannelHandlerContext, msg: EnvelopeProto.Envelope, out: ByteBuf) {
    val framed = EnvelopeFraming.frame(ctx.alloc(), msg)
    try {
      out.writeBytes(framed)
    } finally {
      framed.release()
    }
  }
}

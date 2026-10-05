package net.bestia.zone.socket

import com.google.protobuf.CodedOutputStream
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import net.bestia.bnet.proto.EnvelopeProto

/**
 * The outbound frame: a big-endian four-byte length followed by the serialised envelope.
 *
 * Extracted so there is exactly one definition of it. There are two writers - the per-message encoder in
 * the Netty pipeline, and [ChunkFanOut], which frames once and hands the same bytes to many channels - and
 * a frame format with two implementations is a frame format that will eventually have two behaviours.
 */
object EnvelopeFraming {

  const val LENGTH_FIELD_BYTES = 4

  fun frame(alloc: ByteBufAllocator, envelope: EnvelopeProto.Envelope): ByteBuf {
    val buffer = alloc.buffer(frameSize(envelope))
    writeFrame(envelope, buffer)

    return buffer
  }

  fun frameSize(envelope: EnvelopeProto.Envelope): Int {
    return LENGTH_FIELD_BYTES + envelope.serializedSize
  }

  /** Serialises straight into [out]'s memory, so no byte array is made and nothing is copied. */
  fun writeFrame(envelope: EnvelopeProto.Envelope, out: ByteBuf) {
    val bodySize = envelope.serializedSize
    out.ensureWritable(LENGTH_FIELD_BYTES + bodySize)
    // writeInt is big-endian regardless of the buffer's nominal order.
    out.writeInt(bodySize)

    val body = CodedOutputStream.newInstance(out.nioBuffer(out.writerIndex(), bodySize))
    envelope.writeTo(body)
    body.checkNoSpaceLeft()
    out.writerIndex(out.writerIndex() + bodySize)
  }
}

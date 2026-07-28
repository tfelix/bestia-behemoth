package net.bestia.zone.socket

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
    val body = envelope.toByteArray()
    val buffer = alloc.buffer(LENGTH_FIELD_BYTES + body.size)

    // writeInt is big-endian regardless of the buffer's nominal order, which is why no order() call is
    // needed here - and why one in the caller would be misleading rather than helpful.
    buffer.writeInt(body.size)
    buffer.writeBytes(body)

    return buffer
  }
}

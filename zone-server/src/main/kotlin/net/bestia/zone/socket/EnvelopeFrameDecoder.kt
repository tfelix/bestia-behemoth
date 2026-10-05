package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.TooLongFrameException

/**
 * Splits the inbound stream into length-prefixed frames, with a much smaller limit until the connection has
 * authenticated: every frame is buffered whole before it is decoded, and anybody can connect.
 */
class EnvelopeFrameDecoder(
  private val maxFrameBytesBeforeAuth: Int,
  maxFrameBytes: Int
) : LengthFieldBasedFrameDecoder(maxFrameBytes, 0, LENGTH_BYTES, 0, LENGTH_BYTES) {

  private var authenticated = false

  override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf): Any? {
    if (!authenticated && buffer.readableBytes() >= LENGTH_BYTES) {
      val length = buffer.getUnsignedInt(buffer.readerIndex())

      if (length > maxFrameBytesBeforeAuth) {
        throw TooLongFrameException("Frame of $length bytes before authentication")
      }
    }

    return super.decode(ctx, buffer)
  }

  override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
    if (evt === Authenticated) {
      authenticated = true
    }

    super.userEventTriggered(ctx, evt)
  }

  /** Fired down the pipeline once the connection has authenticated. */
  object Authenticated

  private companion object {
    const val LENGTH_BYTES = EnvelopeFraming.LENGTH_FIELD_BYTES
  }
}

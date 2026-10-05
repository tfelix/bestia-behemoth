package net.bestia.zone.socket

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import net.bestia.bnet.proto.EnvelopeProto

/** The pipeline of one game connection. A plain [Channel] rather than a socket one, so tests can embed it. */
class ZoneChannelInitializer(
  private val handlerContext: ClientMessageHandlerContext
) : ChannelInitializer<Channel>() {

  private val config = handlerContext.socketConfig

  override fun initChannel(ch: Channel) {
    ch.pipeline().addLast(
      EnvelopeFrameDecoder(config.maxFrameBytesBeforeAuth, config.maxFrameBytes),
      // Decoder for protobuf messages
      ProtobufDecoder(EnvelopeProto.Envelope.getDefaultInstance()),
      // Encoder for protobuf messages
      ProtobufEncoder(),
      // Custom encoder for adding big-endian length prefix
      BigEndianLengthFieldPrepender(),
      // Create new handler instance for each connection
      ClientMessageHandler(handlerContext)
    )
  }
}

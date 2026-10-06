package net.bestia.zone.socket

import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.timeout.IdleStateHandler
import net.bestia.bnet.proto.EnvelopeProto
import java.util.concurrent.TimeUnit

/** The pipeline of one game connection. A plain [Channel] rather than a socket one, so tests can embed it. */
class ZoneChannelInitializer(
  private val handlerContext: ClientMessageHandlerContext
) : ChannelInitializer<Channel>() {

  private val config = handlerContext.socketConfig
  private val connectionLimit = ConnectionLimitHandler(config.maxConnectionsPerAddress)

  override fun initChannel(ch: Channel) {
    ch.pipeline().addLast(
      TrafficCounter(handlerContext.traffic),
      // At the head, so it sees the encoded bytes that actually queue up.
      SlowConsumerGuard(config.unwritableTimeoutSeconds, config.maxWriteBacklogBytes, handlerContext.traffic),
      // ClientMessageHandler closes the connection on the idle event.
      IdleStateHandler(config.readIdleTimeoutSeconds, 0, 0, TimeUnit.SECONDS),
      connectionLimit,
      EnvelopeFrameDecoder(config.maxFrameBytesBeforeAuth, config.maxFrameBytes),
      // Decoder for protobuf messages
      ProtobufDecoder(EnvelopeProto.Envelope.getDefaultInstance()),
      // Serialises an Envelope and adds the big-endian length prefix
      BigEndianLengthFieldPrepender(),
      // Create new handler instance for each connection
      ClientMessageHandler(handlerContext)
    )
  }
}

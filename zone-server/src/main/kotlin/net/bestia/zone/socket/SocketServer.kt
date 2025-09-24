package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import jakarta.annotation.PreDestroy
import net.bestia.bnet.proto.EnvelopeProto
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.net.InetSocketAddress

@Service
@Profile("!no-socket")
class SocketServer(
  private val config: SocketServerConfig,
  private val handlerContext: ClientMessageHandlerContext
) {

  private val bossGroup: EventLoopGroup = NioEventLoopGroup()
  private val workerGroup: EventLoopGroup = NioEventLoopGroup()
  private var channelFuture: ChannelFuture? = null

  @Synchronized
  fun start() {
    LOG.info { "Starting socket server..." }
    Thread({
      try {
        val bootstrap = ServerBootstrap()
        bootstrap.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel::class.java)
          .childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
              ch.pipeline().addLast(
                // Decoder for handling length prefix
                LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4),
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
          })

        val socketAddress = InetSocketAddress(config.ipAddress, config.port)
        channelFuture = bootstrap.bind(socketAddress).sync()

        val boundAddress = channelFuture?.channel()?.localAddress()
        LOG.info { "Socket server started on port $boundAddress" }

        // Wait for the server to close
        channelFuture?.channel()?.closeFuture()?.sync()
      } catch (e: InterruptedException) {
        LOG.error(e) { "Server interrupted" }
      } finally {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
      }
    }, "socket-server").start()
  }

  @PreDestroy
  @Synchronized
  fun shutdown() {
    if (channelFuture != null) {
      // the order in which the objects are de-allocated does matter
      bossGroup.shutdownGracefully().sync()
      workerGroup.shutdownGracefully().sync()
      channelFuture?.channel()?.closeFuture()?.sync()
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_FRAME_LENGTH = 1048576 // 1MB
  }
}
package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

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
          .childOption(
            ChannelOption.WRITE_BUFFER_WATER_MARK,
            WriteBufferWaterMark(config.writeBufferLowBytes, config.writeBufferHighBytes)
          )
          .childHandler(ZoneChannelInitializer(handlerContext))

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
  }
}

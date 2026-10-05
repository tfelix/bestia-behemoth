package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.AttributeKey
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Caps the concurrent connections from one IP address. Shared by every channel of the server, so it is first in
 * each pipeline and a refused connection never reaches the handlers behind it.
 *
 * Counts the address the socket sees: if a TCP proxy is ever put in front of the game socket, this needs the
 * proxy protocol, or every player shares the proxy's address.
 */
@ChannelHandler.Sharable
class ConnectionLimitHandler(
  private val maxPerAddress: Int
) : ChannelInboundHandlerAdapter() {

  private val connectionsByAddress = ConcurrentHashMap<String, Int>()

  override fun channelActive(ctx: ChannelHandlerContext) {
    val address = addressOf(ctx.channel())
    var admitted = false

    connectionsByAddress.compute(address) { _, count ->
      val current = count ?: 0
      if (current < maxPerAddress) {
        admitted = true
        current + 1
      } else {
        current
      }
    }

    if (!admitted) {
      LOG.warn { "Refusing connection from $address: it already holds $maxPerAddress" }
      ctx.close()
      return
    }

    ctx.channel().attr(ADMITTED).set(true)
    super.channelActive(ctx)
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    if (ctx.channel().attr(ADMITTED).getAndSet(false) == true) {
      connectionsByAddress.computeIfPresent(addressOf(ctx.channel())) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    super.channelInactive(ctx)
  }

  private fun addressOf(channel: Channel): String {
    val remote = channel.remoteAddress()

    return (remote as? InetSocketAddress)?.address?.hostAddress ?: remote.toString()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    private val ADMITTED: AttributeKey<Boolean> = AttributeKey.valueOf("connectionLimitAdmitted")
  }
}

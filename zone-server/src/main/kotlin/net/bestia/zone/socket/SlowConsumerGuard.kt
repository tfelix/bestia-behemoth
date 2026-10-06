package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Closes the connection of a client that stopped reading, so its unsent messages cannot fill the server's heap.
 *
 * Netty never refuses a write, so this is the only bound on what one connection may queue. A dropped client
 * has to log in again and is then sent its whole view.
 */
class SlowConsumerGuard(
  private val unwritableTimeoutSeconds: Long,
  private val maxWriteBacklogBytes: Long,
  private val traffic: SocketTraffic = SocketTraffic(),
) : ChannelDuplexHandler() {

  private var pendingDrop: ScheduledFuture<*>? = null

  override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
    ctx.write(msg, promise)

    val backlog = ctx.channel().bytesBeforeWritable()
    if (backlog > maxWriteBacklogBytes && drop(ctx, "its write backlog reached $backlog bytes")) {
      traffic.droppedForBacklog()
    }
  }

  override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
    if (ctx.channel().isWritable) {
      cancelPendingDrop()
    } else if (pendingDrop == null) {
      pendingDrop = ctx.executor().schedule({
        if (!ctx.channel().isWritable && drop(ctx, "it stayed unwritable for $unwritableTimeoutSeconds s")) {
          traffic.droppedAsUnwritable()
        }
      }, unwritableTimeoutSeconds, TimeUnit.SECONDS)
    }

    ctx.fireChannelWritabilityChanged()
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    cancelPendingDrop()
    ctx.fireChannelInactive()
  }

  private fun cancelPendingDrop() {
    pendingDrop?.cancel(false)
    pendingDrop = null
  }

  /** Whether this call closed the connection; a backlog keeps growing while the close is under way. */
  private fun drop(ctx: ChannelHandlerContext, reason: String): Boolean {
    if (!ctx.channel().isOpen) return false

    LOG.warn { "Closing ${ctx.channel().remoteAddress()} because $reason" }
    ctx.close()
    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

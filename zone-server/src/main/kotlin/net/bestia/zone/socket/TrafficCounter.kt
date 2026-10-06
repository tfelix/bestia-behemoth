package net.bestia.zone.socket

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise

/** Counts one connection's traffic into [SocketTraffic]. At the head of the pipeline, so it sees framed bytes. */
class TrafficCounter(private val traffic: SocketTraffic) : ChannelDuplexHandler() {

  private var unflushed = 0L

  override fun channelActive(ctx: ChannelHandlerContext) {
    traffic.opened()
    ctx.fireChannelActive()
  }

  override fun channelInactive(ctx: ChannelHandlerContext) {
    traffic.closed()
    ctx.fireChannelInactive()
  }

  override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
    if (msg is ByteBuf) traffic.read(msg.readableBytes())
    ctx.fireChannelRead(msg)
  }

  override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
    if (msg is ByteBuf) unflushed += msg.readableBytes()
    ctx.write(msg, promise)
  }

  override fun flush(ctx: ChannelHandlerContext) {
    traffic.flushed(unflushed)
    unflushed = 0L
    ctx.flush()
  }
}

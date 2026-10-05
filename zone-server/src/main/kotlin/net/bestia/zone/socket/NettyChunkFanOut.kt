package net.bestia.zone.socket

import io.netty.buffer.ByteBufAllocator
import net.bestia.zone.message.SMSG
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Frames the envelope once and writes it to each channel through [ChannelRegistry.writeFramed].
 *
 * Nothing downstream needs to know: the pipeline's outbound encoder matches on `Envelope`, so a raw `ByteBuf`
 * passes through it and reaches the socket exactly as framed.
 */
@Component
@Profile("!no-socket")
class NettyChunkFanOut(
  private val channelRegistry: ChannelRegistry
) : ChunkFanOut {

  override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
    return write(accountIds, message, skipBusy = false)
  }

  /**
   * A chunk payload is three kilobytes, and queuing them behind a slow client is how its backlog grows. Skipped
   * is not lost: the chunk stays un-`markSent` and in `ChunkStreamSystem`'s send queue, which retries it.
   */
  override fun sendToIfWritable(accountId: Long, message: SMSG): Boolean {
    return write(listOf(accountId), message, skipBusy = true) == 1
  }

  private fun write(accountIds: Collection<Long>, message: SMSG, skipBusy: Boolean): Int {
    if (accountIds.isEmpty()) return 0

    val framed = EnvelopeFraming.frame(ByteBufAllocator.DEFAULT, message.toBnetEnvelope())

    return channelRegistry.writeFramed(accountIds, framed, skipBusy)
  }
}

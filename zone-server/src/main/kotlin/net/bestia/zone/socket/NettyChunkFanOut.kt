package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBufAllocator
import net.bestia.zone.message.SMSG
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Frames the envelope once and writes a retained duplicate of it to each channel.
 *
 * A duplicate shares the parent's memory and carries its own reader index, so the fan-out is a refcount
 * increment per recipient rather than a copy - and the last write to complete releases the buffer. Nothing
 * downstream needs to know: the pipeline's outbound encoders both match on specific types, so a raw
 * `ByteBuf` passes through them and reaches the socket exactly as framed.
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

    var written = 0
    try {
      for (accountId in accountIds) {
        val channel = channelRegistry.getChannel(accountId)

        if (channel == null || !channel.isActive) {
          LOG.debug { "No active channel for account $accountId; skipping $message" }
          continue
        }

        if (skipBusy && !channel.isWritable) {
          LOG.debug { "Channel for account $accountId is not writable; skipping $message" }
          continue
        }

        channel.writeAndFlush(framed.retainedDuplicate())
        written++
      }
    } finally {
      // Release the buffer this method owns. Each duplicate holds its own reference until its write
      // completes, so the memory outlives this call exactly as long as it needs to.
      framed.release()
    }

    return written
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

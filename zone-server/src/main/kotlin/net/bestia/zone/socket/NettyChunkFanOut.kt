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
 *
 * ### Unwritable channels are skipped, not queued
 *
 * A chunk stream is the first thing here that can outrun a socket, and Netty's outbound buffer is not a
 * place to put backpressure - an unbounded queue of three-kilobyte payloads behind a slow client is how a
 * server runs out of heap because somebody is on hotel wifi. Skipped is not lost, but it is the caller's to
 * recover: it stays un-`markSent` and in `ChunkStreamSystem`'s own send queue, which retries it. The manifest
 * will not, because it offers what was never announced rather than what never arrived.
 */
@Component
@Profile("!no-socket")
class NettyChunkFanOut(
  private val channelRegistry: ChannelRegistry
) : ChunkFanOut {

  override fun fanOut(accountIds: Collection<Long>, message: SMSG): Int {
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

        if (!channel.isWritable) {
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

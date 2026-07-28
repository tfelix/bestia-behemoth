package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Accepts a client's chunk request and queues it for the tick thread.
 *
 * Deliberately does nothing else. It cannot validate here, because the subscription set it would have to
 * validate against is owned by `zone-tick` and this runs on a Netty worker - see [ChunkStreamInbox]. Gating
 * and rate limiting happen in [ChunkStreamSystem.serveRequests], which is also where they belong: that is
 * the only place that can see the request against the manifest as it stands when the request is served,
 * rather than as it stood when the request was written.
 */
@Component
class ChunkRequestHandler(
  private val inbox: ChunkStreamInbox
) : InMessageProcessor.IncomingMessageHandler<ChunkRequestCMSG> {

  override val handles = ChunkRequestCMSG::class

  override fun handle(msg: ChunkRequestCMSG): Boolean {
    LOG.trace { "Account ${msg.playerId} requested ${msg.chunks.size} chunks" }

    inbox.offerRequest(ChunkStreamInbox.Request(msg.playerId, msg.chunks))

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

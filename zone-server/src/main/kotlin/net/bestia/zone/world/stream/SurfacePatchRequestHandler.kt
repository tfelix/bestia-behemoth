package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Accepts a client's patch request and queues it for the tick thread.
 *
 * Does nothing else, for [ChunkRequestHandler]'s reason: the announced set it would have to validate against
 * belongs to `zone-tick` and this runs on a Netty worker.
 */
@Component
class SurfacePatchRequestHandler(
  private val inbox: ChunkStreamInbox
) : InMessageProcessor.IncomingMessageHandler<SurfacePatchRequestCMSG> {

  override val handles = SurfacePatchRequestCMSG::class

  override fun handle(msg: SurfacePatchRequestCMSG): Boolean {
    LOG.trace { "Account ${msg.playerId} requested ${msg.patches.size} surface patches" }

    inbox.offerPatchRequest(ChunkStreamInbox.PatchRequest(msg.playerId, msg.patches))

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

package net.bestia.zone.world.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.world.stream.ChunkStreamInbox

/**
 * Accepts a client's chunk request and queues it for the tick thread.
 *
 * Deliberately does nothing else. Gating and rate limiting happen in [ChunkStreamSystem.serveRequests],
 * under the stream's own per-tick budget - see [ChunkStreamInbox]. That is also where they belong: that is
 * the only place that can see the request against the manifest as it stands when the request is served,
 * rather than as it stood when the request was written.
 */
@Component
class ChunkRequestHandler(
  private val inbox: ChunkStreamInbox
) : TickMessageHandler<ChunkRequestCMSG> {

  override val wire = decoder(MessageCase.CHUNK_REQUEST) { accountId, envelope ->
    ChunkRequestCMSG.fromBnet(accountId, envelope.chunkRequest)
  }

  override fun handle(world: World, msg: ChunkRequestCMSG): Boolean {
    LOG.trace { "Account ${msg.playerId} requested ${msg.chunks.size} chunks" }

    inbox.offerRequest(ChunkStreamInbox.Request(msg.playerId, msg.chunks))

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

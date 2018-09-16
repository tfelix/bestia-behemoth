package net.bestia.zoneserver.actor.map

import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.messages.map.MapChunkMessage
import net.bestia.messages.map.MapChunkRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.map.MapService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This actor generates a data message containing all the data/map chunks needed
 * for the client to render a certain piece of a map.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class MapRequestChunkActor(
        private val mapService: MapService,
        private val pbService: PlayerEntityService,
        private val entityService: EntityService
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  private fun onMapChunkRequest(msg: MapChunkRequestMessage) {
    // Find the currently active bestia for this account.
    val pbe = pbService.getActivePlayerEntity(msg.accountId)

    val pos = entityService.getComponent(pbe, PositionComponent::class.java)

    if (!pos.isPresent) {
      return
    }

    val point = pos.get().position

    // Verify if the player is able to request the given chunk ids.
    if (!MapService.areChunksInClientRange(point, msg.chunks)) {
      LOG.warn("Player requested invalid chunks. Message: {}", msg)
      return
    }

    val chunks = mapService.getChunks(msg.chunks)
    val response = MapChunkMessage(msg.accountId, chunks)
    sendClient.tell(response, self)
  }

  override fun createReceive(builder: BaseClientMessageRouteActor.BuilderFacade) {
    builder.match(MapChunkRequestMessage::class.java, this::onMapChunkRequest)
  }

  companion object {
    const val NAME = "mapChunk"
  }
}
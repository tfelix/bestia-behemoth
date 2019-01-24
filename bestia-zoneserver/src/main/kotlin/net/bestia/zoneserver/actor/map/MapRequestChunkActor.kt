package net.bestia.zoneserver.actor.map

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.messages.map.MapChunkMessage
import net.bestia.messages.map.MapChunkRequestMessage
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.ActorComponentNoComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRouterActor
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.map.MapService

private val LOG = KotlinLogging.logger { }

/**
 * This actor generates a data message containing all the data/map chunks needed
 * for the client to render a certain piece of a map.
 *
 * @author Thomas Felix
 */
@ActorComponentNoComponent
class MapRequestChunkActor(
    private val mapService: MapService,
    private val pbService: PlayerEntityService,
    private val messageApi: MessageApi
) : DynamicMessageRouterActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  private fun onMapChunkRequest(msg: MapChunkRequestMessage) {
    val activeEntityId = pbService.getActivePlayerEntityId(msg.accountId) ?: return
    awaitEntityResponse(messageApi, context, activeEntityId) {
      val posComp = it.getComponent(PositionComponent::class.java)
      val point = posComp.position

      // Verify if the player is able to request the given chunk ids.
      if (!MapService.areChunksInClientRange(point, msg.chunks)) {
        LOG.warn("Player requested invalid chunks. Message: {}", msg)
        return@awaitEntityResponse
      }

      val chunks = mapService.getChunks(msg.chunks)
      val response = MapChunkMessage(msg.accountId, chunks)
      sendClient.tell(response, self)
    }
  }

  override fun createReceive(builder: DynamicMessageRouterActor.BuilderFacade) {
    builder.match(MapChunkRequestMessage::class.java, this::onMapChunkRequest)
  }

  companion object {
    const val NAME = "mapChunk"
  }
}
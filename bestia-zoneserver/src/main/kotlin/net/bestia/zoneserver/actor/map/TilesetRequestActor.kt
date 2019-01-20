package net.bestia.zoneserver.actor.map

import mu.KotlinLogging
import net.bestia.messages.map.MapTilesetMessage
import net.bestia.messages.map.MapTilesetRequestMessage
import net.bestia.model.map.TilesetData
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.map.TilesetService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * The user queries the name/data of an [TilesetData]. He only sends the
 * GID of the tile and we must find the appropriate tileset.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class TilesetRequestActor(
    private val tilesetService: TilesetService
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(MapTilesetRequestMessage::class.java, this::onMapTilesetRequest)
  }

  private fun onMapTilesetRequest(msg: MapTilesetRequestMessage) {

    val ts = tilesetService.findTileset(msg.tileId)
    if (ts == null) {
      LOG.warn { "Tileset containing gid ${msg.tileId} not found." }
      return
    }

    val response = MapTilesetMessage(
        msg.accountId,
        ts.toTilesetDTO()
    )

    sendClient.tell(response, self)
  }

  companion object {
    const val NAME = "tileset"
  }
}
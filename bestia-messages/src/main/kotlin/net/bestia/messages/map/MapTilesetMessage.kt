package net.bestia.messages.map

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.map.Tileset.SimpleTileset

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 *
 * @author Thomas Felix
 */
data class MapTilesetMessage(
    override val accountId: Long,

    @JsonProperty("ts")
    val tileset: SimpleTileset
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.tileset"
  }
}

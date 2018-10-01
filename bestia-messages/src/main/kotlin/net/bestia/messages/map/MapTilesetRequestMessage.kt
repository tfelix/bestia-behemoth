package net.bestia.messages.map

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Tiles are only referenced by so called gids. This ids are globally unique and
 * can be found inside the tilesets. These tilest data can be requested from the
 * server via such an request.
 *
 * @author Thomas Felix
 */
data class MapTilesetRequestMessage(
    override val accountId: Long,

    @JsonProperty("gid")
    val tileId: Int
) : AccountMessage, MessageId {
  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.tilesetrequest"
  }
}

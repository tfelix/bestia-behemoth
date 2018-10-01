package net.bestia.messages.map

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.map.MapChunk

/**
 * This message contains all needed information for the client to load and
 * display a piece of a map.
 *
 * @author Thomas Felix
 */
data class MapChunkMessage(
    override val accountId: Long,
    @JsonProperty("c")
    private val chunks: List<MapChunk>
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.chunk"
  }
}

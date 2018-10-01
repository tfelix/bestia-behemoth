package net.bestia.messages.map

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.geometry.Point

/**
 * Asks the server to send the data of the map in the current viewport (with
 * some additional extra data to buffer some movements).
 *
 * @author Thomas Felix
 */
data class MapChunkRequestMessage(
    override val accountId: Long,

    @JsonProperty("c")
    val chunks: List<Point>
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.requestdata"
  }
}

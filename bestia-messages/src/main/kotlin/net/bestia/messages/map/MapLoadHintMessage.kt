package net.bestia.messages.map

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId
import net.bestia.model.domain.SpriteInfo

/**
 * This message is send by the server to the client which has newly connected to
 * a map. It will contain assets of currently active entities or bestias on this
 * map and suggests a loading of these. The server regenerates this list from
 * time to time and resends it.
 *
 * @author Thomas Felix
 */
data class MapLoadHintMessage(
    override val accountId: Long,

    @JsonProperty("s")
    val sprites: List<SpriteInfo>
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "map.loadhint"
  }
}

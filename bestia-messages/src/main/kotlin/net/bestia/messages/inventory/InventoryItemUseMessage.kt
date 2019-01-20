package net.bestia.messages.inventory

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Signals the server to use an castable item on the map possibly spawning map
 * entities or doing things to the map/zone itself.
 *
 * @author Thomas Felix
 */
data class InventoryItemUseMessage(
    override val accountId: Long,

    @JsonProperty("piid")
    val playerItemId: Int,
    /**
     * Token for identifying the cast request on the client and receive the
     * confirm message.
     */
    @JsonProperty("t")
    val token: String,
    val x: Int = 0,
    val y: Int = 0
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "inventory.item.cast"
  }
}

package net.bestia.messages.inventory

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Send if the player wants to drop an item to the ground.
 *
 * @author Thomas Felix
 */
data class InventoryItemDropMessage(
    override val accountId: Long,

    @JsonProperty("iid")
    val itemId: Int,

    @JsonProperty("a")
    val amount: Int
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "inventory.drop"
  }
}

package net.bestia.messages.inventory

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * The server confirms the casting of an item to the client.
 *
 * @author Thomas Felix
 */
data class InventoryItemCastConfirmMessage(
    override val accountId: Long,

    @JsonProperty("s")
    val success: Boolean,

    @JsonProperty("t")
    val token: String
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "inventory.item.castconfirm"
  }
}

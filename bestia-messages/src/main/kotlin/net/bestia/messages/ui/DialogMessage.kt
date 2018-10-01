package net.bestia.messages.ui

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * This message will trigger the client to open a dialog NPC box.
 *
 * @author Thomas Felix
 */
data class DialogMessage(
    override val accountId: Long,
    @JsonProperty("n")
    private val nodes: List<DialogNode>
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "ui.dialog"
  }
}

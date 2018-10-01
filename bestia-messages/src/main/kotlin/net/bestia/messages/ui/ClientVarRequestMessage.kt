package net.bestia.messages.ui

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * Asks the server to replay with a list of shortcuts for the current entity and
 * the account.
 *
 * @author Thomas Felix
 */
data class ClientVarRequestMessage(
    override val accountId: Long,
    val key: String,
    var uuid: String,
    @JsonProperty("d")
    var data: String
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "cvar.req"
  }
}

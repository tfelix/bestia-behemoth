package net.bestia.messages.ui

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.MessageId


data class ClientVarResponseMessage(
        @get:JsonProperty("uuid")
        val uuid: String,

        @get:JsonProperty("d")
        val data: String
) : MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "cvar"
  }
}

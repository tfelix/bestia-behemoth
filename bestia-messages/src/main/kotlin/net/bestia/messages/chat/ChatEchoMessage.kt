package net.bestia.messages.chat

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

data class ChatEchoMessage(
    override val accountId: Long,

    @JsonProperty("ec")
    var echoCode: EchoCode,

    @JsonProperty("txt")
    val text: String,

    @JsonProperty("cmid")
    val chatMessageId: Int
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  enum class EchoCode {
    OK, ERROR, RECEIVER_UNKNOWN
  }

  companion object {
    const val MESSAGE_ID = "chat.echo"
  }
}

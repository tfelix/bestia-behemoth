package net.bestia.messages.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage

/**
 * Chatmessage is sent from the user to the server and vice versa.
 *
 * @author Thomas Felix
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    @JsonProperty("aid")
    override val accountId: Long,

    @JsonProperty("m")
    val chatMode: Mode,

    @JsonProperty("txt")
    val text: String,

    @JsonProperty("sn")
    private var senderNickname: String? = null,

    @JsonProperty("rxn")
    val receiverNickname: String? = null,

    @JsonProperty("cmid")
    val chatMessageId: Int = 0,

    @JsonProperty("t")
    val time: Long = 0,

    @JsonProperty("eid")
    val entityId: Long? = null
) : AccountMessage {

  val messageId: String
    get() = MESSAGE_ID

  enum class Mode {
    PUBLIC, PARTY, GUILD, WHISPER, SYSTEM, GM_BROADCAST, ERROR, COMMAND, BATTLE
  }

  companion object {
    const val MESSAGE_ID = "chat.message"

    /**
     * Creates a new chat message in the mode as a system message.
     *
     * @param accId
     * A account to receive the message.
     * @param text
     * A text to send to the client.
     * @return The generated message.
     */
    fun getSystemMessage(accId: Long, text: String): ChatMessage {
      return ChatMessage(
          accountId = accId,
          chatMode = Mode.SYSTEM,
          text = text,
          time = System.currentTimeMillis() / 1000L
      )
    }
  }
}
package net.bestia.messages.chat

import net.bestia.messages.AccountMessage

/**
 * Chatmessage is sent from the user to the server and vice versa.
 *
 * @author Thomas Felix
 */
data class ChatMessage(
    override val accountId: Long,
    val chatMode: Mode,
    val text: String,
    private var senderNickname: String? = null,
    val receiverNickname: String? = null,
    val chatMessageId: Int = 0,
    val time: Long = 0,
    val entityId: Long? = null
) : AccountMessage {

  enum class Mode {
    PUBLIC, PARTY, GUILD, WHISPER, SYSTEM, GM_BROADCAST, ERROR, COMMAND, BATTLE
  }

  companion object {
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

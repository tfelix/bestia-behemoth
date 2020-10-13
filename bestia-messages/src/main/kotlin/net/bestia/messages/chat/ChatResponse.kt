package net.bestia.messages.chat

import net.bestia.messages.AccountMessage

/**
 * Chatmessage is sent from the user to the server and vice versa.
 *
 * @author Thomas Felix
 */
data class ChatResponse(
    override val accountId: Long,
    val chatMode: ChatMode,
    val text: String,
    var senderNickname: String? = null,
    val time: Long = 0,
    val entityId: Long? = null
) : AccountMessage {

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
    fun getSystemMessage(accId: Long, text: String): ChatResponse {
      return ChatResponse(
          accountId = accId,
          chatMode = ChatMode.SYSTEM,
          text = text,
          time = System.currentTimeMillis() / 1000L
      )
    }
  }
}

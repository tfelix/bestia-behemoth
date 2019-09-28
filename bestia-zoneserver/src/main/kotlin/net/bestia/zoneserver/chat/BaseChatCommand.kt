package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.routing.MessageApi

internal abstract class BaseChatCommand(
    protected val messageApi: MessageApi
) : ChatCommand {

  /**
   * Defines a help text in case the arguments of the command are not ready.
   *
   * @return A short helping text for the command.
   */
  protected abstract val helpText: String

  /**
   * This text is send right to the user who invoked the command.
   *
   * @param text  The text to send to the user.
   * @param accId The account id to send the text to.
   */
  protected fun sendSystemMessage(accId: Long, text: String) {
    val replyMsg = ChatMessage.getSystemMessage(accId, text)
    messageApi.send(ClientEnvelope(accId, replyMsg))
  }
}

package net.bestia.messages.login

import net.bestia.messages.MessageId

/**
 * This message is send to the player in order to signal a (forced) logout from
 * the system.
 *
 * @author Thomas Felix
 */
data class LogoutMessage(
        val state: LogoutState = LogoutState.NO_REASON,
        val reason: String = ""
) : MessageId {

  override fun getMessageId(): String {
    return MESSAGE_ID
  }

  companion object {
    const val MESSAGE_ID = "system.logout"
  }
}
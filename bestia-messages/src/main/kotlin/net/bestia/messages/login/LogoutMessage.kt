package net.bestia.messages.login

import net.bestia.messages.MessageId

/**
 * This message is send to the player in order to signal a (forced) logout from
 * the system.
 *
 * @author Thomas Felix
 */
data class LogoutMessage(
    val state: LoginResponse = LoginResponse.NO_REASON,
    val reason: String = ""
) : MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "system.logout"
  }
}
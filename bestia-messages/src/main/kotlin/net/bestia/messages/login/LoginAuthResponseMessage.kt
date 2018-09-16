package net.bestia.messages.login

import net.bestia.messages.MessageId

data class LoginAuthResponseMessage(
        val response: LoginError
): MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "system.authresp"
  }
}
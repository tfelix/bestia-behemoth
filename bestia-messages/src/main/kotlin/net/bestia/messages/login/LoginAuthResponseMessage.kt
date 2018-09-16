package net.bestia.messages.login

import net.bestia.messages.MessageId

enum class AuthResponse {
  LOGIN_WRONG,
  LOGIN_NOT_ALLOWED,
  LOGIN_OK
}

data class LoginAuthResponseMessage(
        val response: AuthResponse
): MessageId {

  override fun getMessageId(): String {
    return MESSAGE_ID
  }

  companion object {
    const val MESSAGE_ID = "system.authresp"
  }
}
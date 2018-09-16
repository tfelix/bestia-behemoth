package net.bestia.messages.login

import net.bestia.messages.MessageId

/**
 * Message is send if a webserver wants to authenticate a pending connection. It
 * will send the given access token from the request to the login server which
 * must respond accordingly.
 *
 * @author Thomas Felix
 */
data class LoginAuthRequestMessage(
        /**
         * User provided login token which will be checked against in the database.
         *
         * @return Login token.
         */
        val token: String
) : MessageId {

  override fun getMessageId(): String {
    return MESSAGE_ID
  }

  companion object {
    const val MESSAGE_ID = "system.loginauth"
  }
}

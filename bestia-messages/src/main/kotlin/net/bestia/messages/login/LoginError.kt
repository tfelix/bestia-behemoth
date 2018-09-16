package net.bestia.messages.login

enum class LoginError {
  /**
   * Connection was closed without reason. Client should try to reconnect.
   */
  NO_REASON,

  /**
   * There was an internal server error while processing the request. Try to reconnect.
   */
  SERVER_ERROR,

  /**
   * Currently no logins are allowed. Client should not retry.
   */
  NO_LOGINS_ALLOWED
}

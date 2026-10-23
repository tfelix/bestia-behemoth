package net.bestia.login.gamelogin

/**
 * A login attempt that cannot proceed. [error] is what the caller is told, and follows from [reason]; the message
 * is for the log only, because the client has no use for the distinction between a code that never
 * existed, one that expired, and one that was already spent. The reason is for the metrics, which count it.
 */
class GameLoginException(
  val reason: GameLoginReason,
  message: String,
  cause: Throwable? = null
) : Exception(message, cause) {

  val error: GameLoginError
    get() = reason.error
}

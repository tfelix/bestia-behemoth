package net.bestia.login.gamelogin

/**
 * A login attempt that cannot proceed. [GameLoginError] is what the caller is told; the message is
 * for the log only, because the client has no use for the distinction between a code that never
 * existed, one that expired, and one that was already spent.
 */
class GameLoginException(
  val error: GameLoginError,
  message: String,
  cause: Throwable? = null
) : Exception(message, cause)

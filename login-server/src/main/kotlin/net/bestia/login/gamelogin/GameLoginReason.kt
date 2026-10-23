package net.bestia.login.gamelogin

/**
 * Why a login step was refused, as finely as the log and the metrics want it. [GameLoginError] is all the caller is
 * told, and is fixed by the reason: each reason belongs to exactly one of them.
 */
enum class GameLoginReason(val error: GameLoginError) {
  /** A start request whose challenge method, challenge or state is not what the protocol allows. */
  MALFORMED_PARAMETER(GameLoginError.INVALID_REQUEST),

  /** A redirect URI the client may not send the code to. */
  REDIRECT_REJECTED(GameLoginError.INVALID_REQUEST),

  /** The login session does not exist - never did, or was swept. */
  NO_SESSION(GameLoginError.INVALID_GRANT),

  /** The login session exists but has expired, or is not usable in its status. */
  SESSION_NOT_USABLE(GameLoginError.INVALID_GRANT),

  /** The login session was in another status than the step needs, or lost the race to move on. */
  SESSION_STATE(GameLoginError.INVALID_GRANT),

  /** The login session belongs to another browser than the one asking. */
  BROWSER_BINDING(GameLoginError.INVALID_GRANT),

  /** The code is unknown, expired or already spent. */
  CODE_NOT_REDEEMABLE(GameLoginError.INVALID_GRANT),

  /** The code, or the refresh token, was issued to the other kind of client. */
  WRONG_CLIENT(GameLoginError.INVALID_GRANT),

  /** The verifier does not open the session's PKCE challenge. */
  PKCE_MISMATCH(GameLoginError.INVALID_GRANT),

  /** The code and its login session name different accounts. */
  ACCOUNT_MISMATCH(GameLoginError.INVALID_GRANT),

  /** The account the grant names no longer exists. */
  MISSING_ACCOUNT(GameLoginError.INVALID_GRANT),

  /** The refresh token is unknown, expired, revoked or spent. */
  REFRESH_REFUSED(GameLoginError.INVALID_GRANT),

  /** The account may not log in right now. */
  BANNED(GameLoginError.ACCOUNT_UNAVAILABLE);

  /** The metric tag value. */
  val tag: String = name.lowercase()
}

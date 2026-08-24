package net.bestia.login.gamelogin

/**
 * Deliberately coarse. Anything finer would let a caller probe which half of a code/verifier pair
 * it got wrong, and there is nothing the game could usefully do differently anyway.
 */
enum class GameLoginError {
  /** The start request was malformed - bad redirect URI, unsupported challenge method. */
  INVALID_REQUEST,

  /** No usable session or code: never existed, expired, or already spent. */
  INVALID_GRANT,

  /** The account exists but may not log in right now. */
  ACCOUNT_UNAVAILABLE
}

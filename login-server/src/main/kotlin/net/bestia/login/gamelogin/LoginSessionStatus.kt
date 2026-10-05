package net.bestia.login.gamelogin

enum class LoginSessionStatus {
  /** Started by the game, waiting for the browser to finish authenticating. */
  PENDING,

  /** WebAuthn succeeded; the browser may still enrol a passkey before returning to the game. */
  AUTHENTICATED,

  /** The single authorization code has been issued and waits for the game to exchange it. */
  CODE_ISSUED,

  /** The code was exchanged for a token. Terminal. */
  CONSUMED
}

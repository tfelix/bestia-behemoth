package net.bestia.login.gamelogin

enum class LoginSessionStatus {
  /** Started by the game, waiting for the browser to finish authenticating. */
  PENDING,

  /** WebAuthn succeeded and an authorization code has been issued. */
  AUTHENTICATED,

  /** The code was exchanged for a token. Terminal. */
  CONSUMED
}

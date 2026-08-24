package net.bestia.login.gamelogin

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "game-login")
data class GameLoginConfig(
  /** How long the player has to finish the browser half of the flow. */
  val sessionTtlSeconds: Long = 300,

  /**
   * Lifetime of the single-use code handed to the game's loopback listener. Short on purpose: the
   * hop from browser to listener is a redirect on the same machine, so anything beyond a few
   * seconds is only widening the window for a local process that managed to observe it.
   */
  val authorizationCodeTtlSeconds: Long = 60,

  /** Base URL the game is told to open. Must be one of `webauthn.origins`. */
  val publicBaseUrl: String,

  val recoveryCodeCount: Int = 10
)

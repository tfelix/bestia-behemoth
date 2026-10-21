package net.bestia.login.gamelogin

/** Who a login is for. Decides which redirect targets are allowed and which tokens come out of it. */
enum class LoginClient {
  /** The Godot client, redirected to its own loopback listener. */
  GAME,

  /** The companion web app, redirected to one of `companion-login.redirect-uris`. */
  COMPANION,
}

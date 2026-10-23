package net.bestia.login.gamelogin

import org.springframework.stereotype.Component

/**
 * The companion app's redirect target must be one of a fixed list, compared as a whole string. A web app has no
 * loopback listener to fall back on, so the boundary [RedirectUriValidator] draws for the game is drawn here by
 * naming every page an authorization code may be sent to.
 */
@Component
class CompanionRedirectValidator(private val config: CompanionLoginConfig) {

  fun validate(raw: String) {
    if (raw !in config.redirectUris) {
      throw GameLoginException(GameLoginReason.REDIRECT_REJECTED, "redirect_uri is not an allowed companion page")
    }
  }
}

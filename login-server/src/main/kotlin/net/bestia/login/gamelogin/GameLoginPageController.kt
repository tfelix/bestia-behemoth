package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.webauthn.WebAuthnConfig
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * Serves the page the game opens in the system browser.
 *
 * The session identifier is handed to the page as a data attribute rather than interpolated into a
 * script block, so there is no context in which it could be read as markup or code.
 */
@Controller
class GameLoginPageController(
  private val loginSessionService: LoginSessionService,
  private val webAuthnConfig: WebAuthnConfig
) {

  @GetMapping("/game-login")
  fun page(
    @RequestParam("session") sessionId: String,
    @RequestParam("intent", required = false) intent: String?,
    model: Model
  ): String {
    try {
      loginSessionService.requireUsable(sessionId)
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Refusing to render game login page" }
      model.addAttribute("reason", "This sign-in link has expired. Start again from the game.")

      return "login-expired"
    }

    model.addAttribute("sessionId", sessionId)
    model.addAttribute("rpName", webAuthnConfig.rpName)
    model.addAttribute("register", intent.equals(LoginIntent.REGISTER.name, ignoreCase = true))

    return "game-login"
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

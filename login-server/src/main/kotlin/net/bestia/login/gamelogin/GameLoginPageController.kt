package net.bestia.login.gamelogin

import java.time.Duration
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.http.ResponseCookie
import org.springframework.http.HttpHeaders
import jakarta.servlet.http.HttpServletResponse
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
  private val webAuthnConfig: WebAuthnConfig,
  private val gameLoginConfig: GameLoginConfig
) {

  @GetMapping("/game-login")
  fun page(
    @RequestParam("session") sessionId: String,
    @RequestParam("intent", required = false) intent: String?,
    @CookieValue(name = LoginSessionService.BINDING_COOKIE, required = false) presentedBinding: String?,
    response: HttpServletResponse,
    model: Model
  ): String {
    val binding = try {
      loginSessionService.claimForBrowser(sessionId, presentedBinding)
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Refusing to render game login page" }
      null
    }

    // Also what a second browser sees: the link already belongs to the one that opened it first.
    if (binding == null) {
      model.addAttribute("reason", "This sign-in link has expired. Start again from the game.")

      return "login-expired"
    }

    response.addHeader(HttpHeaders.SET_COOKIE, bindingCookie(binding).toString())

    model.addAttribute("sessionId", sessionId)
    model.addAttribute("rpName", webAuthnConfig.rpName)
    model.addAttribute("register", intent.equals(LoginIntent.REGISTER.name, ignoreCase = true))

    return "game-login"
  }

  private fun bindingCookie(binding: String): ResponseCookie {
    return ResponseCookie.from(LoginSessionService.BINDING_COOKIE, binding)
      .httpOnly(true)
      .secure(gameLoginConfig.bindingCookieSecure)
      .sameSite("Strict")
      .path("/")
      .maxAge(Duration.ofSeconds(gameLoginConfig.sessionTtlSeconds))
      .build()
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

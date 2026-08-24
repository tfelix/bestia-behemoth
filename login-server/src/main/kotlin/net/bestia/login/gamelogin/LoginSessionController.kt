package net.bestia.login.gamelogin

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.ratelimit.RateLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Called by the browser, once, when the player is actually ready to go back to the game.
 *
 * Minting the authorization code here rather than at the end of the ceremony is deliberate. The
 * code lives sixty seconds, and the page between authenticating and returning may hold a list of
 * recovery codes to write down or an offer to enrol a second passkey - both of which take longer
 * than the code would survive.
 */
@RestController
@RequestMapping("/api/v1/auth/session")
class LoginSessionController(
  private val loginSessionService: LoginSessionService,
  private val authorizationCodeService: AuthorizationCodeService,
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard,
  private val rateLimiter: RateLimiter
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class CompleteRequest(
    val sessionId: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class CompleteResponse(
    val redirectTo: String
  )

  data class CompleteFailure(
    val error: String
  )

  @PostMapping("/complete")
  fun complete(
    @RequestBody request: CompleteRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!rateLimiter.tryAcquire("session-complete:${servletRequest.remoteAddr}", REQUESTS_PER_WINDOW, WINDOW)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CompleteFailure("Too many requests"))
    }

    return try {
      val session = loginSessionService.requireAuthenticated(request.sessionId)
      val accountId = session.accountId!!

      val account = accounts.findById(accountId).orElseThrow {
        GameLoginException(GameLoginError.INVALID_GRANT, "session refers to a missing account")
      }

      // Re-checked here and not only at authentication time: a ban applied while the player was
      // reading their recovery codes should still take effect.
      accountLoginGuard.denialReason(account)?.let { reason ->
        throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
      }

      val code = authorizationCodeService.issue(session, accountId)
      val encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8)
      val encodedState = URLEncoder.encode(session.clientState, StandardCharsets.UTF_8)

      // The redirect URI was validated at /start to carry no query of its own, so appending is
      // unambiguous.
      ResponseEntity.ok(
        CompleteResponse(redirectTo = "${session.redirectUri}?code=$encodedCode&state=$encodedState")
      )
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Refusing to complete login session" }
      ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CompleteFailure("Sign-in could not be completed"))
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val REQUESTS_PER_WINDOW = 20
    private val WINDOW: Duration = Duration.ofMinutes(1)
  }
}

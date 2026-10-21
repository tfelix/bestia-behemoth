package net.bestia.login.gamelogin

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.login.jwt.JwtService
import net.bestia.login.ratelimit.RateLimiter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * The companion web app's login: the game's flow - PKCE, the same passkey page, a single-use code - with two
 * differences. The code is sent to an allowlisted page of the app rather than a loopback listener, and the
 * refresh token never reaches the page's script: it lives in an httpOnly cookie scoped to these endpoints.
 *
 * Everything that comes out is a companion token, signed with its own key; nothing here can mint a zone token.
 */
@RestController
@RequestMapping("/api/v1/auth/companion")
class CompanionLoginController(
  private val loginSessionService: LoginSessionService,
  private val loginGrants: LoginGrants,
  private val refreshTokenService: RefreshTokenService,
  private val jwtService: JwtService,
  private val gameLoginConfig: GameLoginConfig,
  private val companionConfig: CompanionLoginConfig,
  private val rateLimiter: RateLimiter,
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class StartRequest(
    val redirectUri: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val state: String,
    val intent: LoginIntent = LoginIntent.LOGIN,
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class StartResponse(val sessionId: String, val loginUrl: String, val expiresIn: Long)

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class ExchangeRequest(val code: String, val codeVerifier: String)

  /** [accountId] so the app can tell whose cached data it holds; [expiresIn] in seconds. */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class TokenResponse(val accessToken: String, val accountId: Long, val expiresIn: Long)

  data class Failure(val error: GameLoginError)

  @PostMapping("/start")
  fun start(@RequestBody request: StartRequest, servletRequest: HttpServletRequest): ResponseEntity<*> {
    if (!allow(servletRequest, "start")) {
      return tooManyRequests()
    }

    return try {
      val started = loginSessionService.start(
        redirectUri = request.redirectUri,
        codeChallenge = request.codeChallenge,
        challengeMethod = request.codeChallengeMethod,
        clientState = request.state,
        intent = request.intent,
        client = LoginClient.COMPANION,
      )
      ResponseEntity.ok(StartResponse(started.sessionId, started.loginUrl, started.expiresInSeconds))
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected companion login start" }
      failure(e)
    }
  }

  @PostMapping("/exchange")
  fun exchange(@RequestBody request: ExchangeRequest, servletRequest: HttpServletRequest): ResponseEntity<*> {
    if (!allow(servletRequest, "exchange")) {
      return tooManyRequests()
    }

    return try {
      val account = loginGrants.redeemCode(request.code, request.codeVerifier, LoginClient.COMPANION)
      tokens(account.id, refreshTokenService.issueForNewSession(account.id, LoginClient.COMPANION))
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected companion login exchange" }
      failure(e)
    }
  }

  /** A new access token, from the refresh cookie. The cookie is rotated on every call. */
  @PostMapping("/refresh")
  fun refresh(
    @CookieValue(REFRESH_COOKIE, required = false) refreshToken: String?,
    servletRequest: HttpServletRequest,
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "refresh")) {
      return tooManyRequests()
    }
    if (refreshToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Failure(GameLoginError.INVALID_GRANT))
    }

    return try {
      val (account, successor) = loginGrants.refresh(refreshToken, LoginClient.COMPANION)
      tokens(account.id, successor)
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected companion refresh" }
      failure(e, clearCookie = true)
    }
  }

  /** Signs this browser out: its chain of refresh tokens stops working, and the cookie goes. */
  @PostMapping("/logout")
  fun logout(@CookieValue(REFRESH_COOKIE, required = false) refreshToken: String?): ResponseEntity<Void> {
    refreshToken?.let { refreshTokenService.revokeFamilyOf(it) }

    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build()
  }

  private fun tokens(accountId: Long, refreshToken: String): ResponseEntity<TokenResponse> {
    val lifetime = Duration.ofDays(gameLoginConfig.refreshTokenDays)
    val body = TokenResponse(jwtService.createCompanionToken(accountId), accountId, jwtService.companionTokenMillis / 1000)

    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(refreshToken, lifetime)).body(body)
  }

  private fun cookie(value: String, maxAge: Duration): String {
    return ResponseCookie.from(REFRESH_COOKIE, value)
      .httpOnly(true)
      .secure(gameLoginConfig.bindingCookieSecure)
      .sameSite("Strict")
      .path(companionConfig.refreshCookiePath)
      .maxAge(maxAge)
      .build()
      .toString()
  }

  private fun allow(request: HttpServletRequest, bucket: String): Boolean {
    return rateLimiter.tryAcquire("companion-$bucket:${request.remoteAddr}", REQUESTS_PER_WINDOW, WINDOW)
  }

  private fun tooManyRequests(): ResponseEntity<Failure> {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Failure(GameLoginError.INVALID_REQUEST))
  }

  /** As at the game's endpoints, the enum is all the client is told; the reason is in the log. */
  private fun failure(e: GameLoginException, clearCookie: Boolean = false): ResponseEntity<Failure> {
    val status = when (e.error) {
      GameLoginError.INVALID_REQUEST -> HttpStatus.BAD_REQUEST
      GameLoginError.INVALID_GRANT -> HttpStatus.UNAUTHORIZED
      GameLoginError.ACCOUNT_UNAVAILABLE -> HttpStatus.FORBIDDEN
    }

    val response = ResponseEntity.status(status)
    if (clearCookie) {
      response.header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
    }
    return response.body(Failure(e.error))
  }

  companion object {
    const val REFRESH_COOKIE = "bestia_companion_refresh"

    private val LOG = KotlinLogging.logger { }
    private const val REQUESTS_PER_WINDOW = 20
    private val WINDOW: Duration = Duration.ofMinutes(1)
  }
}

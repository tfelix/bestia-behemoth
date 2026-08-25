package net.bestia.login.gamelogin

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.jwt.JwtService
import net.bestia.login.ratelimit.RateLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime

/**
 * The calls the game itself makes. Everything between /start and /exchange happens in the system
 * browser; /refresh replaces all three on every start after the first.
 *
 * The client is untrusted throughout: it chooses the redirect target (validated to a loopback
 * literal), the PKCE challenge (which it must later prove it can open) and the state nonce (which
 * is only ever echoed back). Nothing it sends decides which account is authenticated.
 */
@RestController
@RequestMapping("/api/v1/auth/game")
class GameLoginController(
  private val loginSessionService: LoginSessionService,
  private val authorizationCodeService: AuthorizationCodeService,
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard,
  private val refreshTokenService: RefreshTokenService,
  private val jwtService: JwtService,
  private val rateLimiter: RateLimiter
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class StartRequest(
    val redirectUri: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val state: String,
    val intent: LoginIntent = LoginIntent.LOGIN
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class StartResponse(
    val sessionId: String,
    val loginUrl: String,
    val expiresIn: Long
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class ExchangeRequest(
    val code: String,
    val codeVerifier: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class RefreshRequest(
    val refreshToken: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class RevokeRequest(
    val refreshToken: String
  )

  /**
   * What both /exchange and /refresh return, so the client has one token-handling path regardless of
   * whether this start went through the browser.
   *
   * [refreshToken] is always a fresh one. The client must replace what it stored: the token it sent
   * is spent, and presenting it again is what the server reads as a stolen copy.
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class ExchangeResponse(
    val token: String,
    val refreshToken: String
  )

  data class GameLoginFailure(
    val error: GameLoginError,
    val message: String?
  )

  @PostMapping("/start")
  fun start(
    @RequestBody request: StartRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "start")) {
      return tooManyRequests()
    }

    return try {
      val started = loginSessionService.start(
        redirectUri = request.redirectUri,
        codeChallenge = request.codeChallenge,
        challengeMethod = request.codeChallengeMethod,
        clientState = request.state,
        intent = request.intent
      )

      ResponseEntity.ok(
        StartResponse(
          sessionId = started.sessionId,
          loginUrl = started.loginUrl,
          expiresIn = started.expiresInSeconds
        )
      )
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected game login start" }
      failure(e)
    }
  }

  @PostMapping("/exchange")
  fun exchange(
    @RequestBody request: ExchangeRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "exchange")) {
      return tooManyRequests()
    }

    return try {
      // Claim first. If the same code arrives twice, only one caller gets past this line, and it
      // happens before the verifier is examined so a wrong verifier still burns the code.
      val code = authorizationCodeService.consume(request.code)

      val session = loginSessionService.findByHash(code.loginSessionIdHash)
        ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "code has no login session")

      if (!Pkce.verify(request.codeVerifier, session.codeChallenge, session.challengeMethod)) {
        throw GameLoginException(GameLoginError.INVALID_GRANT, "PKCE verifier does not match")
      }

      val account = accounts.findById(code.accountId).orElseThrow {
        GameLoginException(GameLoginError.INVALID_GRANT, "code refers to a missing account")
      }

      accountLoginGuard.denialReason(account)?.let { reason ->
        throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
      }

      loginSessionService.markConsumed(session.idHash)

      account.lastLogin = LocalDateTime.now()
      accounts.save(account)

      ResponseEntity.ok(
        ExchangeResponse(
          token = jwtService.createLoginToken(account.id, account.role),
          refreshToken = refreshTokenService.issueForNewSession(account.id)
        )
      )
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected game login exchange" }
      failure(e)
    }
  }

  /**
   * The whole point of the exercise: a client that already has a refresh token starts the game
   * without a browser, a loopback listener or a passkey prompt.
   *
   * Nothing about the passkey ceremony is repeated here, so everything that could have changed since
   * it happened has to be re-examined: the token may have been revoked, and the account may have
   * been banned. Both are checked before a zone token is minted.
   */
  @PostMapping("/refresh")
  fun refresh(
    @RequestBody request: RefreshRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "refresh")) {
      return tooManyRequests()
    }

    return try {
      val rotated = refreshTokenService.rotate(request.refreshToken)

      if (rotated !is RefreshTokenService.Rotation.Rotated) {
        throw GameLoginException(GameLoginError.INVALID_GRANT, "refresh token could not be rotated")
      }

      val account = accounts.findById(rotated.accountId).orElseThrow {
        GameLoginException(GameLoginError.INVALID_GRANT, "refresh token refers to a missing account")
      }

      accountLoginGuard.denialReason(account)?.let { reason ->
        // The successor minted a moment ago goes too. An account that may not log in has no business
        // holding a standing invitation to do so on any of its devices.
        refreshTokenService.revokeAllForAccount(account.id)

        throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
      }

      account.lastLogin = LocalDateTime.now()
      accounts.save(account)

      ResponseEntity.ok(
        ExchangeResponse(
          token = jwtService.createLoginToken(account.id, account.role),
          refreshToken = rotated.token
        )
      )
    } catch (e: GameLoginException) {
      LOG.debug(e) { "Rejected refresh" }
      failure(e)
    }
  }

  /**
   * Signs one client out: the chain the presented token belongs to stops working, on the server and
   * not only on the machine that asked.
   *
   * Answers the same way whether the token was live, already dead or never existed. There is nothing
   * a caller could do with the difference except find out whether a token it guessed is real.
   */
  @PostMapping("/revoke")
  fun revoke(
    @RequestBody request: RevokeRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<Void> {
    if (!allow(servletRequest, "revoke")) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
    }

    refreshTokenService.revokeFamilyOf(request.refreshToken)

    return ResponseEntity.noContent().build()
  }

  /**
   * Keyed on the socket address rather than a forwarded header: there is no reverse proxy in front
   * of this server today, and trusting X-Forwarded-For without one lets any caller pick its own
   * bucket.
   */
  private fun allow(request: HttpServletRequest, bucket: String): Boolean {
    return rateLimiter.tryAcquire("$bucket:${request.remoteAddr}", REQUESTS_PER_WINDOW, WINDOW)
  }

  private fun tooManyRequests(): ResponseEntity<GameLoginFailure> {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
      .body(GameLoginFailure(GameLoginError.INVALID_REQUEST, "Too many requests"))
  }

  private fun failure(e: GameLoginException): ResponseEntity<GameLoginFailure> {
    val status = when (e.error) {
      GameLoginError.INVALID_REQUEST -> HttpStatus.BAD_REQUEST
      GameLoginError.INVALID_GRANT -> HttpStatus.BAD_REQUEST
      GameLoginError.ACCOUNT_UNAVAILABLE -> HttpStatus.FORBIDDEN
    }

    // The enum is all the client is told. The reason lives in the log, because distinguishing an
    // expired code from a spent one from a wrong verifier only helps whoever is guessing.
    return ResponseEntity.status(status).body(GameLoginFailure(e.error, null))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val REQUESTS_PER_WINDOW = 20
    private val WINDOW: Duration = Duration.ofMinutes(1)
  }
}

package net.bestia.login.webauthn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.gamelogin.GameLoginException
import net.bestia.login.gamelogin.LoginSessionService
import net.bestia.login.recovery.AccountRecoveryService
import net.bestia.login.ratelimit.RateLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * The browser half of the flow. Called by the login page's JavaScript, never by the game.
 *
 * There is no cookie anywhere in here. The login session identifier travels in the page URL and
 * back in the request body, so these endpoints carry no ambient authority for a cross-site request
 * to ride on, and CSRF has nothing to attack.
 */
@RestController
@RequestMapping("/api/v1/webauthn")
class WebAuthnController(
  private val registrationService: WebAuthnRegistrationService,
  private val assertionService: WebAuthnAssertionService,
  private val loginSessionService: LoginSessionService,
  private val recoveryService: AccountRecoveryService,
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard,
  private val rateLimiter: RateLimiter,
  private val objectMapper: ObjectMapper
) {

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class AssertOptionsRequest(
    val sessionId: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class RegisterOptionsRequest(
    val sessionId: String,
    val displayName: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class AddCredentialRequest(
    val sessionId: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class RecoverOptionsRequest(
    val sessionId: String,
    val displayName: String,
    val recoveryCode: String
  )

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class OptionsResponse(
    val ceremonyId: String,
    /** The inner options object, ready for `PublicKeyCredential.parse*OptionsFromJSON`. */
    val publicKey: JsonNode
  )

  /**
   * The credential is forwarded as the JSON the browser produced. The library parses that exact
   * wire format itself, and re-modelling it here would only create a second representation to
   * drift from the first.
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class VerifyRequest(
    val ceremonyId: String,
    val credential: JsonNode
  )

  /**
   * Carries no redirect. The authorization code is minted separately, once the player says they
   * are ready to return, because it lives only a minute and this page may still have recovery
   * codes to be written down or a second passkey to enrol.
   */
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
  data class VerifyResponse(
    val authenticated: Boolean,
    val recoveryCodes: List<String> = emptyList()
  )

  data class WebAuthnFailure(
    val error: String
  )

  @PostMapping("/assert/options")
  fun assertOptions(
    @RequestBody request: AssertOptionsRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "assert-options")) {
      return tooManyRequests()
    }

    return handle {
      val session = loginSessionService.requireUsable(request.sessionId)
      val started = assertionService.start(session.idHash)

      OptionsResponse(
        ceremonyId = started.ceremonyId,
        publicKey = publicKeyOf(started.request.toCredentialsGetJson())
      )
    }
  }

  @PostMapping("/assert/verify")
  fun assertVerify(
    @RequestBody request: VerifyRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "assert-verify")) {
      return tooManyRequests()
    }

    return handle {
      val finished = assertionService.finish(
        request.ceremonyId,
        objectMapper.writeValueAsString(request.credential)
      )

      bindLoginSession(finished.loginSessionIdHash, finished.accountId)

      VerifyResponse(authenticated = true)
    }
  }

  @PostMapping("/register/options")
  fun registerOptions(
    @RequestBody request: RegisterOptionsRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "register-options")) {
      return tooManyRequests()
    }

    return handle {
      val session = loginSessionService.requireUsable(request.sessionId)
      val started = registrationService.startAccountRegistration(request.displayName, session.idHash)

      OptionsResponse(
        ceremonyId = started.ceremonyId,
        publicKey = publicKeyOf(started.request.toCredentialsCreateJson())
      )
    }
  }

  @PostMapping("/register/verify")
  fun registerVerify(
    @RequestBody request: VerifyRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "register-verify")) {
      return tooManyRequests()
    }

    return handle {
      val finished = registrationService.finishRegistration(
        request.ceremonyId,
        objectMapper.writeValueAsString(request.credential)
      )

      bindLoginSession(finished.loginSessionIdHash, finished.accountId)

      VerifyResponse(authenticated = true, recoveryCodes = finished.recoveryCodes)
    }
  }

  /**
   * Enrols an extra passkey or security key on the account that has just authenticated, before the
   * player returns to the game.
   *
   * Authorized by the login session itself. It is in the AUTHENTICATED state, which only a
   * completed ceremony can produce, and its identifier is a 256-bit secret held by this browser tab
   * alone - which is why adding a credential needs neither a cookie nor a second login scheme.
   */
  @PostMapping("/credentials/options")
  fun addCredentialOptions(
    @RequestBody request: AddCredentialRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "credential-options")) {
      return tooManyRequests()
    }

    return handle {
      val session = loginSessionService.requireAuthenticated(request.sessionId)

      val started = registrationService.startCredentialRegistration(
        accountId = requireAccount(session.accountId),
        loginSessionIdHash = session.idHash
      )

      OptionsResponse(
        ceremonyId = started.ceremonyId,
        publicKey = publicKeyOf(started.request.toCredentialsCreateJson())
      )
    }
  }

  @PostMapping("/credentials/verify")
  fun addCredentialVerify(
    @RequestBody request: VerifyRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "credential-verify")) {
      return tooManyRequests()
    }

    return handle {
      registrationService.finishRegistration(
        request.ceremonyId,
        objectMapper.writeValueAsString(request.credential)
      )

      VerifyResponse(authenticated = true)
    }
  }

  /**
   * Redeems a recovery code and returns the options for the replacement passkey.
   *
   * The code by itself signs nobody in. The account only becomes reachable again once the new
   * credential has been created and verified, so a leaked code is worth nothing without also
   * completing a ceremony on a real authenticator.
   */
  @PostMapping("/recover/options")
  fun recoverOptions(
    @RequestBody request: RecoverOptionsRequest,
    servletRequest: HttpServletRequest
  ): ResponseEntity<*> {
    if (!allow(servletRequest, "recover", RECOVERY_ATTEMPTS_PER_WINDOW, RECOVERY_WINDOW)) {
      return tooManyRequests()
    }

    return handle {
      val session = loginSessionService.requireUsable(request.sessionId)

      val started = recoveryService.startRecovery(
        displayName = request.displayName,
        recoveryCode = request.recoveryCode,
        loginSessionIdHash = session.idHash
      )

      OptionsResponse(
        ceremonyId = started.ceremonyId,
        publicKey = publicKeyOf(started.request.toCredentialsCreateJson())
      )
    }
  }

  /**
   * Marks the login session as belonging to the account that has just authenticated.
   *
   * The session comes from the ceremony row rather than from anything sent alongside the
   * credential. If the browser could name the session, an assertion obtained during one login
   * attempt could be attached to a different one.
   */
  private fun bindLoginSession(loginSessionIdHash: String?, accountId: Long) {
    if (loginSessionIdHash == null) {
      return
    }

    val session = loginSessionService.findByHash(loginSessionIdHash)
      ?: throw WebAuthnException("Ceremony refers to a login session that no longer exists")

    val account = accounts.findById(accountId).orElseThrow {
      WebAuthnException("Authenticated account no longer exists")
    }

    accountLoginGuard.denialReason(account)?.let { reason ->
      LOG.info { "Refusing to bind login session: $reason" }
      throw WebAuthnException("Account may not log in")
    }

    loginSessionService.markAuthenticated(session, accountId)
  }

  private fun requireAccount(accountId: Long?): Long {
    return accountId ?: throw WebAuthnException("Authenticated session carries no account")
  }

  /**
   * `toCredentialsGetJson` / `toCredentialsCreateJson` emit the whole `navigator.credentials`
   * argument. The page only wants what goes under `publicKey`, which is what the browser's
   * `parse*OptionsFromJSON` helpers consume.
   */
  private fun publicKeyOf(credentialsJson: String): JsonNode {
    return objectMapper.readTree(credentialsJson).get("publicKey")
      ?: throw WebAuthnException("Ceremony options carried no publicKey member")
  }

  private fun allow(
    request: HttpServletRequest,
    bucket: String,
    limit: Int = REQUESTS_PER_WINDOW,
    window: Duration = WINDOW
  ): Boolean {
    return rateLimiter.tryAcquire("$bucket:${request.remoteAddr}", limit, window)
  }

  private fun tooManyRequests(): ResponseEntity<WebAuthnFailure> {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(WebAuthnFailure("Too many requests"))
  }

  /**
   * One failure string for every way a ceremony can fail. Telling the page whether the credential
   * was unknown, the challenge stale, or the signature wrong hands a probe to whoever is guessing.
   */
  private fun <T : Any> handle(block: () -> T): ResponseEntity<*> {
    return try {
      ResponseEntity.ok(block())
    } catch (e: WebAuthnException) {
      LOG.debug(e) { "WebAuthn ceremony rejected" }
      badRequest()
    } catch (e: GameLoginException) {
      LOG.debug(e) { "WebAuthn ceremony rejected" }
      badRequest()
    }
  }

  private fun badRequest(): ResponseEntity<WebAuthnFailure> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(WebAuthnFailure(GENERIC_ERROR))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val GENERIC_ERROR = "Authentication failed"
    private const val REQUESTS_PER_WINDOW = 30
    private val WINDOW: Duration = Duration.ofMinutes(1)

    // Recovery is the weakest link in an account model that holds no email address, so it gets its
    // own, far tighter budget.
    private const val RECOVERY_ATTEMPTS_PER_WINDOW = 5
    private val RECOVERY_WINDOW: Duration = Duration.ofMinutes(15)
  }
}

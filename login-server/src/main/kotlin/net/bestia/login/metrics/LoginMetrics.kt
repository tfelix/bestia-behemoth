package net.bestia.login.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.account.KickReason
import net.bestia.login.gamelogin.GameLoginReason
import net.bestia.login.gamelogin.LoginClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.EnumMap

/**
 * Everything the login server counts, registered up front so every series exists at zero from the first scrape and
 * a rate over it has something to start from. Tags are small enums, never an address, a token or an account.
 *
 * The counters are Micrometer's own and are bumped from request threads, which they allow. The state the database
 * holds is not here: [LoginStateMeters] reads that at scrape time.
 */
@Component
class LoginMetrics(private val registry: MeterRegistry = SimpleMeterRegistry()) {

  enum class AssertionResult { OK, MALFORMED, UNKNOWN_CREDENTIAL, FAILED }

  /** The browser-facing ceremony calls of `WebAuthnController`, one per endpoint. */
  enum class CeremonyStep {
    ASSERT_OPTIONS, ASSERT_VERIFY, REGISTER_OPTIONS, REGISTER_VERIFY, CREDENTIAL_OPTIONS, CREDENTIAL_VERIFY,
    RECOVER_OPTIONS
  }

  enum class CeremonyResult {
    OK,

    /** The one failure the page is told apart: a passkey this server has no row for. */
    UNKNOWN_CREDENTIAL,

    /** Anything the ceremony itself refused: bad challenge, bad signature, bad name, a wrong recovery code. */
    REJECTED,

    /** The login session behind the call was refused - another browser, expired, wrong status, banned. */
    SESSION_REFUSED,
  }

  enum class GrantFlow { START, EXCHANGE, REFRESH }

  enum class TokenType { ZONE, API, COMPANION, REFRESH }

  enum class RefreshRefusal { UNKNOWN, WRONG_CLIENT, REVOKED_OR_EXPIRED }

  enum class RefreshRevocation {
    /** A spent token was presented again: the chain is treated as stolen. */
    REUSE,

    /** A client signed itself out. */
    SIGN_OUT,

    /** An account's sessions were ended (ban, GM kick, recovery); counts accounts, not tokens. */
    ACCOUNT_TERMINATED,
  }

  enum class AuthCodeEvent { ISSUED, CONSUMED, REFUSED }

  enum class RegistrationKind { ACCOUNT, ADD_CREDENTIAL, RECOVERY }

  enum class RecoveryResult {
    /** The code was good and the replacement ceremony was opened. */
    STARTED,

    /** Wrong name or wrong code, which the endpoint deliberately does not tell apart. */
    REJECTED,

    /** The right code for an account that may not log in. */
    BANNED,
  }

  enum class GmAction { KICK, BAN }

  enum class SweepJob { AUTHORIZATION_CODE, LOGIN_SESSION, REFRESH_TOKEN, WEBAUTHN_CEREMONY, RATE_LIMIT }

  private val assertions = enumCounters<AssertionResult>("login.assertion") { "result" to it.tag() }

  private val assertionVerify = Timer.builder("login.assertion.verify")
    .description("Time the WebAuthn library spends verifying an assertion")
    .publishPercentileHistogram()
    .register(registry)

  private val ceremonies = CeremonyStep.entries.associateWith { step ->
    CeremonyResult.entries.associateWith { result ->
      counter("login.ceremony", "step" to step.tag(), "result" to result.tag())
    }
  }

  private val grants = LoginClient.entries.associateWith { client ->
    GrantFlow.entries.associateWith { flow ->
      mapOf(
        true to counter("login.grant", "client" to client.tag(), "flow" to flow.tag(), "result" to "ok"),
        false to counter("login.grant", "client" to client.tag(), "flow" to flow.tag(), "result" to "refused"),
      )
    }
  }

  private val grantRefusals = LoginClient.entries.associateWith { client ->
    GrantFlow.entries.associateWith { flow ->
      GameLoginReason.entries.associateWith { reason ->
        counter("login.grant.refused", "client" to client.tag(), "flow" to flow.tag(), "reason" to reason.tag)
      }
    }
  }

  private val sessionRefusals = GameLoginReason.entries.associateWith {
    counter("login.session.refused", "reason" to it.tag)
  }

  private val tokensIssued = enumCounters<TokenType>("login.token.issued") { "type" to it.tag() }
  private val tokensInvalid = counter("login.token.invalid")

  private val refreshRotations = mapOf(
    true to counter("login.refresh.rotations", "result" to "rotated"),
    false to counter("login.refresh.rotations", "result" to "refused"),
  )
  private val refreshRefusals = enumCounters<RefreshRefusal>("login.refresh.refused") { "reason" to it.tag() }
  private val refreshReuse = counter("login.refresh.reuse.detected")
  private val refreshRevocations = enumCounters<RefreshRevocation>("login.refresh.revocations") { "cause" to it.tag() }

  private val authCodes = enumCounters<AuthCodeEvent>("login.authcode") { "event" to it.tag() }
  private val registrations = enumCounters<RegistrationKind>("login.registration") { "kind" to it.tag() }
  private val recoveries = enumCounters<RecoveryResult>("login.recovery") { "result" to it.tag() }

  private val gmActions = GmAction.entries.associateWith { action ->
    mapOf(
      true to counter("login.gm.action", "action" to action.tag(), "result" to "ok"),
      false to counter("login.gm.action", "action" to action.tag(), "result" to "refused"),
    )
  }

  private val rateLimitDenials = (RATE_LIMIT_BUCKETS + OTHER_BUCKET).associateWith {
    counter("login.ratelimit.denied", "bucket" to it)
  }

  private val kicksPublished = KickReason.entries.associateWith { reason ->
    mapOf(
      true to counter("login.kick.published", "reason" to reason.name, "result" to "ok"),
      false to counter("login.kick.published", "reason" to reason.name, "result" to "error"),
    )
  }

  private val kickPublishTimers = mapOf(
    true to kickTimer("ok"),
    false to kickTimer("error"),
  )

  private val sweepRemoved = enumCounters<SweepJob>("login.sweep.removed") { "job" to it.tag() }
  private val sweepFailed = enumCounters<SweepJob>("login.sweep.failed") { "job" to it.tag() }
  private val sweepDuration = SweepJob.entries.associateWith {
    Timer.builder("login.sweep.duration").tag("job", it.tag()).register(registry)
  }

  fun assertion(result: AssertionResult) {
    assertions.getValue(result).increment()
  }

  fun assertionVerified(elapsedNanos: Long) {
    assertionVerify.record(Duration.ofNanos(elapsedNanos))
  }

  fun ceremony(step: CeremonyStep, result: CeremonyResult) {
    ceremonies.getValue(step).getValue(result).increment()
  }

  fun grantOk(client: LoginClient, flow: GrantFlow) {
    grants.getValue(client).getValue(flow).getValue(true).increment()
  }

  fun grantRefused(client: LoginClient, flow: GrantFlow, reason: GameLoginReason) {
    grants.getValue(client).getValue(flow).getValue(false).increment()
    grantRefusals.getValue(client).getValue(flow).getValue(reason).increment()
  }

  /** A refusal in the browser half of the flow, which has no client or flow of its own to be told apart by. */
  fun sessionRefused(reason: GameLoginReason) {
    sessionRefusals.getValue(reason).increment()
  }

  fun tokenIssued(type: TokenType) {
    tokensIssued.getValue(type).increment()
  }

  fun tokenInvalid() {
    tokensInvalid.increment()
  }

  fun refreshRotated() {
    refreshRotations.getValue(true).increment()
  }

  fun refreshRefused(reason: RefreshRefusal) {
    refreshRotations.getValue(false).increment()
    refreshRefusals.getValue(reason).increment()
  }

  /** A spent token came back. The chain dies with it, so this is counted as a refusal and as a revocation too. */
  fun refreshReused() {
    refreshRotations.getValue(false).increment()
    refreshReuse.increment()
    refreshRevocations.getValue(RefreshRevocation.REUSE).increment()
  }

  fun refreshRevoked(cause: RefreshRevocation) {
    refreshRevocations.getValue(cause).increment()
  }

  fun authCode(event: AuthCodeEvent) {
    authCodes.getValue(event).increment()
  }

  fun registration(kind: RegistrationKind) {
    registrations.getValue(kind).increment()
  }

  fun recovery(result: RecoveryResult) {
    recoveries.getValue(result).increment()
  }

  fun gmAction(action: GmAction, allowed: Boolean) {
    gmActions.getValue(action).getValue(allowed).increment()
  }

  /** [key] is a rate-limit key, `<bucket>:<address>`; only the bucket is kept, and only when it is a known one. */
  fun rateLimitDenied(key: String) {
    val bucket = key.substringBefore(':')

    rateLimitDenials.getValue(if (bucket in RATE_LIMIT_BUCKETS) bucket else OTHER_BUCKET).increment()
  }

  fun kickPublished(reason: KickReason, succeeded: Boolean, elapsedNanos: Long) {
    kicksPublished.getValue(reason).getValue(succeeded).increment()
    kickPublishTimers.getValue(succeeded).record(Duration.ofNanos(elapsedNanos))
  }

  /** Runs one of the scheduled sweeps, counting what it removed and how long it took. A failure is counted and rethrown. */
  fun sweep(job: SweepJob, block: () -> Int) {
    val startedAt = System.nanoTime()

    try {
      sweepRemoved.getValue(job).increment(block().toDouble())
    } catch (e: Exception) {
      sweepFailed.getValue(job).increment()
      throw e
    } finally {
      sweepDuration.getValue(job).record(Duration.ofNanos(System.nanoTime() - startedAt))
    }
  }

  private fun kickTimer(result: String): Timer {
    return Timer.builder("login.kick.publish.duration")
      .description("Time appending a kick order to the bus stream took, inside the request that caused the kick")
      .tag("result", result)
      .publishPercentileHistogram()
      .register(registry)
  }

  private fun counter(name: String, vararg tags: Pair<String, String>): Counter {
    return Counter.builder(name).tags(*tags.flatMap { listOf(it.first, it.second) }.toTypedArray()).register(registry)
  }

  private inline fun <reified E : Enum<E>> enumCounters(
    name: String,
    tag: (E) -> Pair<String, String>
  ): Map<E, Counter> {
    return enumValues<E>().associateWithTo(EnumMap(E::class.java)) { counter(name, tag(it)) }
  }

  private fun Enum<*>.tag(): String {
    return name.lowercase()
  }

  companion object {
    /** The prefixes of every key the rate limiter is called with, which is what keeps the `bucket` tag bounded. */
    val RATE_LIMIT_BUCKETS = listOf(
      "start", "exchange", "refresh", "revoke",
      "companion-start", "companion-exchange", "companion-refresh",
      "assert-options", "assert-verify", "register-options", "register-verify",
      "credential-options", "credential-verify", "recover", "session-complete",
    )

    private const val OTHER_BUCKET = "other"
  }
}

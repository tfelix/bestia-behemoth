package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the counters say after the flows they count. Each test watches the series it expects to move, plus one that
 * must not, so a counter that bumps on every path would be caught too.
 */
class LoginMetricsScenario : BasePasskeyScenario() {

  @Test
  fun `a passkey login and exchange are counted`() {
    val registered = watch("login.registration", "kind", "account")
    val registerVerify = watch("login.ceremony", "step", "register_verify", "result", "ok")
    val assertionOk = watch("login.assertion", "result", "ok")
    val exchangeOk = watch("login.grant", "client", "game", "flow", "exchange", "result", "ok")
    val codeIssued = watch("login.authcode", "event", "issued")
    val codeConsumed = watch("login.authcode", "event", "consumed")
    val zone = watch("login.token.issued", "type", "zone")
    val api = watch("login.token.issued", "type", "api")
    val refresh = watch("login.token.issued", "type", "refresh")
    val companion = watch("login.token.issued", "type", "companion")

    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    exchangeFully(registration.code, registration.verifier)
    val second = signIn(authenticator, registration.userHandle)
    exchangeFully(second.code, second.verifier)

    assertEquals(1.0, registered.moved())
    assertEquals(1.0, registerVerify.moved())
    assertEquals(1.0, assertionOk.moved())
    assertEquals(2.0, exchangeOk.moved())
    assertEquals(2.0, codeIssued.moved())
    assertEquals(2.0, codeConsumed.moved())
    assertEquals(2.0, zone.moved())
    assertEquals(2.0, api.moved())
    assertEquals(2.0, refresh.moved())
    assertEquals(0.0, companion.moved(), "a game login must not mint a companion token")
  }

  @Test
  fun `a wrong PKCE verifier is a refused grant with its own reason, and still burns the code`() {
    val pkce = watch("login.grant.refused", "client", "game", "flow", "exchange", "reason", "pkce_mismatch")
    val refused = watch("login.grant", "client", "game", "flow", "exchange", "result", "refused")
    val ok = watch("login.grant", "client", "game", "flow", "exchange", "result", "ok")
    val consumed = watch("login.authcode", "event", "consumed")
    val registration = register(VirtualAuthenticator())

    assertEquals(400, rawExchange(registration.code, "not-the-verifier-it-was-started-with-0000000").statusCode.value())

    assertEquals(1.0, pkce.moved())
    assertEquals(1.0, refused.moved())
    assertEquals(0.0, ok.moved())
    assertEquals(1.0, consumed.moved())
  }

  @Test
  fun `a code exchanged twice is refused the second time as not redeemable`() {
    val spent = watch("login.grant.refused", "client", "game", "flow", "exchange", "reason", "code_not_redeemable")
    val authCodeRefused = watch("login.authcode", "event", "refused")
    val registration = register(VirtualAuthenticator())
    exchangeFully(registration.code, registration.verifier)

    assertEquals(400, rawExchange(registration.code, registration.verifier).statusCode.value())

    assertEquals(1.0, spent.moved())
    assertEquals(1.0, authCodeRefused.moved())
  }

  @Test
  fun `a companion login is counted as the companion's, and mints no zone token`() {
    val startOk = watch("login.grant", "client", "companion", "flow", "start", "result", "ok")
    val gameStart = watch("login.grant", "client", "game", "flow", "start", "result", "ok")

    start(path = "/api/v1/auth/companion/start", redirectUri = COMPANION_REDIRECT)

    assertEquals(1.0, startOk.moved())
    assertEquals(0.0, gameStart.moved())
  }

  @Test
  fun `a refused redirect is counted as such`() {
    val rejected = watch("login.grant.refused", "client", "game", "flow", "start", "reason", "redirect_rejected")

    val response = rawPost("/api/v1/auth/game/start", startBody("https://evil.example/callback", "state-state-state"))

    assertEquals(400, response.statusCode.value())
    assertEquals(1.0, rejected.moved())
  }

  @Test
  fun `a refresh token presented after it was spent is counted as reuse and takes its chain with it`() {
    val reuse = watch("login.refresh.reuse.detected")
    val reuseRevocation = watch("login.refresh.revocations", "cause", "reuse")
    val rotated = watch("login.refresh.rotations", "result", "rotated")
    val refused = watch("login.grant.refused", "client", "game", "flow", "refresh", "reason", "refresh_refused")
    val registration = register(VirtualAuthenticator())
    val stolen = exchangeFully(registration.code, registration.verifier).refreshToken

    refresh(stolen)
    assertEquals(400, rawRefresh(stolen).statusCode.value())

    assertEquals(1.0, reuse.moved())
    assertEquals(1.0, reuseRevocation.moved())
    assertEquals(1.0, rotated.moved(), "only the first presentation rotated")
    assertEquals(1.0, refused.moved())
  }

  @Test
  fun `an unknown refresh token is refused without being counted as reuse`() {
    val reuse = watch("login.refresh.reuse.detected")
    val unknown = watch("login.refresh.refused", "reason", "unknown")

    assertEquals(400, rawRefresh("a-refresh-token-this-server-never-issued").statusCode.value())

    assertEquals(0.0, reuse.moved())
    assertEquals(1.0, unknown.moved())
  }

  @Test
  fun `signing out is counted as a revocation`() {
    val signOut = watch("login.refresh.revocations", "cause", "sign_out")
    val registration = register(VirtualAuthenticator())
    val tokens = exchangeFully(registration.code, registration.verifier)

    assertEquals(204, rawRevoke(tokens.refreshToken).statusCode.value())

    assertEquals(1.0, signOut.moved())
  }

  @Test
  fun `exhausting the limiter counts the denial under its bucket, never the address`() {
    val denied = watch("login.ratelimit.denied", "bucket", "start")
    val other = watch("login.ratelimit.denied", "bucket", "exchange")
    val body = startBody("http://127.0.0.1:$LOOPBACK_PORT/callback", "state-state-state")

    repeat(REQUESTS_PER_WINDOW) {
      assertEquals(200, rawPost("/api/v1/auth/game/start", body).statusCode.value())
    }
    assertEquals(429, rawPost("/api/v1/auth/game/start", body).statusCode.value())

    // At least: the test client retries a 429 once, which the limiter counts as a second denial.
    assertTrue(denied.moved() >= 1.0, "the 21st call was denied, so the start bucket must have counted it")
    assertEquals(0.0, other.moved())
  }

  @Test
  fun `a malformed assertion response is counted as malformed`() {
    val malformed = watch("login.assertion", "result", "malformed")
    val rejected = watch("login.ceremony", "step", "assert_verify", "result", "rejected")
    val session = start()
    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId), session.cookie)

    val response = rawPost(
      "/api/v1/webauthn/assert/verify",
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapOf("id" to "x")),
      session.cookie
    )

    assertEquals(400, response.statusCode.value())
    assertEquals(1.0, malformed.moved())
    assertEquals(1.0, rejected.moved())
  }

  @Test
  fun `a page calling with another browser's session is a refused session`() {
    val binding = watch("login.session.refused", "reason", "browser_binding")
    val rejected = watch("login.ceremony", "step", "assert_options", "result", "session_refused")
    val session = start()

    val response = rawPost("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId), null)

    assertEquals(400, response.statusCode.value())
    assertEquals(1.0, binding.moved())
    assertEquals(1.0, rejected.moved())
  }

  @Test
  fun `a recovery is counted with its kick`() {
    val started = watch("login.recovery", "result", "started")
    val rejected = watch("login.recovery", "result", "rejected")
    val recovered = watch("login.registration", "kind", "recovery")
    val kick = watch("login.kick.published", "reason", "RECOVERED", "result", "ok")
    val displayName = uniqueDisplayName()
    val owner = register(VirtualAuthenticator(), displayName)

    val session = start()
    val wrong = rawPost(
      "/api/v1/webauthn/recover/options",
      mapOf("session_id" to session.sessionId, "display_name" to displayName, "recovery_code" to "not-a-code"),
      session.cookie
    )
    recover(VirtualAuthenticator(), displayName, owner.recoveryCodes.first())

    assertEquals(400, wrong.statusCode.value())
    assertEquals(1.0, rejected.moved())
    assertEquals(1.0, started.moved())
    assertEquals(1.0, recovered.moved())
    assertEquals(1.0, kick.moved())
  }

  private companion object {
    const val REQUESTS_PER_WINDOW = 20
    const val COMPANION_REDIRECT = "http://localhost:5173/auth/callback"
  }
}

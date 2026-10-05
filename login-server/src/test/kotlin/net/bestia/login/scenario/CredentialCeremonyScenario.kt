package net.bestia.login.scenario

import com.fasterxml.jackson.databind.JsonNode
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.AccountStatus
import net.bestia.login.gamelogin.LoginSessionService
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * Adding a passkey is the one step that survives every later revocation, so it is only finished as what it
 * was started as, on a session that signed in moments ago, for an account that may still log in.
 */
class CredentialCeremonyScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var accounts: AccountRepository

  @Autowired
  private lateinit var loginSessionService: LoginSessionService

  @Autowired
  private lateinit var jdbc: JdbcTemplate

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `a recovery ceremony cannot be finished as an extra passkey`() {
    val displayName = uniqueDisplayName()
    val registration = register(VirtualAuthenticator(), displayName)
    val session = start()
    val options = post(
      "/api/v1/webauthn/recover/options",
      mapOf("session_id" to session.sessionId, "display_name" to displayName, "recovery_code" to registration.recoveryCodes[0]),
      session.cookie
    )

    assertEquals(400, finishAt(CREDENTIALS_VERIFY, options, session).statusCode.value())
  }

  @Test
  fun `a new account ceremony cannot be finished as an extra passkey`() {
    val session = start()
    val options = post(
      "/api/v1/webauthn/register/options",
      mapOf("session_id" to session.sessionId, "display_name" to uniqueDisplayName()),
      session.cookie
    )

    assertEquals(400, finishAt(CREDENTIALS_VERIFY, options, session).statusCode.value())
  }

  @Test
  fun `a banned account cannot finish adding a passkey`() {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val accountId = accountIdOf(exchange(registration.code, registration.verifier))
    val session = start()
    assertOn(session, authenticator, registration.userHandle)
    val options = post("/api/v1/webauthn/credentials/options", mapOf("session_id" to session.sessionId), session.cookie)

    committed {
      val account = accounts.findById(accountId).orElseThrow()
      account.status = AccountStatus.PERMA_BANNED
      accounts.save(account)
    }

    assertEquals(400, finishAt(CREDENTIALS_VERIFY, options, session).statusCode.value())
  }

  @Test
  fun `a passkey cannot be added long after the sign in`() {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val session = start()
    assertOn(session, authenticator, registration.userHandle)

    committed {
      jdbc.update(
        "UPDATE login_session SET authenticated_at = ? WHERE id_hash = ?",
        LocalDateTime.now().minusMinutes(3),
        loginSessionService.hash(session.sessionId)
      )
    }

    val response = rawPost("/api/v1/webauthn/credentials/options", mapOf("session_id" to session.sessionId), session.cookie)

    assertEquals(400, response.statusCode.value())
  }

  private fun finishAt(path: String, options: JsonNode, session: StartedSession): ResponseEntity<String> {
    val credential = VirtualAuthenticator().create(
      webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin()
    )

    return rawPost(
      path,
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential)),
      session.cookie
    )
  }

  /** The scenario runs in a test transaction the server cannot see into, so writes it must see are committed. */
  private fun committed(block: () -> Unit) {
    val template = TransactionTemplate(transactionManager)
    template.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    template.executeWithoutResult { block() }
  }

  private companion object {
    const val CREDENTIALS_VERIFY = "/api/v1/webauthn/credentials/verify"
  }
}

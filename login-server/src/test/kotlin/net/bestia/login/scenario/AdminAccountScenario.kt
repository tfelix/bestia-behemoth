package net.bestia.login.scenario

import net.bestia.account.Role
import net.bestia.internal.ServiceTokens
import net.bestia.login.webauthn.VirtualAuthenticator
import net.bestia.login.zone.ZoneStub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/** GMs ban and kick players from the game client, through the login server's REST API. */
class AdminAccountScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var zone: ZoneStub

  @Autowired
  private lateinit var jdbc: JdbcTemplate

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Test
  fun `a GM bans a player, who can no longer resume`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    val response = admin("ban", gm, player.accountId, mapOf("note" to "botting", "duration_hours" to 24))

    assertEquals(204, response.statusCode.value())
    // 400, not 403: the ban revoked the refresh token before any account check could see the ban.
    assertEquals(400, rawRefresh(player.refreshToken).statusCode.value())
    assertTrue(zone.calls.any { it.path == ServiceTokens.kickPath(player.accountId) && it.body.contains("BANNED") })
  }

  @Test
  fun `a GM kicks a player from the zones`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    val response = admin("kick", gm, player.accountId, mapOf("note" to "stuck"))

    assertEquals(204, response.statusCode.value())
    assertTrue(zone.calls.any { it.path == ServiceTokens.kickPath(player.accountId) && it.body.contains("GM_KICK") })
  }

  @Test
  fun `every GM action is written down`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    admin("kick", gm, player.accountId, mapOf("note" to "stuck"))

    val logged = jdbc.queryForObject(
      "SELECT COUNT(*) FROM gm_action WHERE actor_account_id = ? AND target_account_id = ? AND action = 'KICK'",
      Long::class.java,
      gm.accountId,
      player.accountId
    )
    assertEquals(1L, logged)
  }

  @Test
  fun `a player cannot ban anyone`() {
    val player = signedIn(Role.USER)
    val other = signedIn(Role.USER)

    assertEquals(403, admin("ban", player, other.accountId, mapOf("note" to "x")).statusCode.value())
  }

  @Test
  fun `a GM cannot act against another GM`() {
    val gm = signedIn(Role.GM)
    val other = signedIn(Role.GM)

    assertEquals(403, admin("ban", gm, other.accountId, mapOf("note" to "x")).statusCode.value())
  }

  /** The token only names the account. What it may do is read again on every call. */
  @Test
  fun `a GM who was banned can no longer ban`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)
    committed { jdbc.update("UPDATE account SET status = 'PERMA_BANNED' WHERE id = ?", gm.accountId) }

    assertEquals(403, admin("ban", gm, player.accountId, mapOf("note" to "x")).statusCode.value())
  }

  @Test
  fun `a zone login token does not open the admin api`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    val response = admin("ban", gm.copy(apiToken = gm.zoneToken), player.accountId, mapOf("note" to "x"))

    assertEquals(401, response.statusCode.value())
  }

  private data class SignedIn(
    val accountId: Long,
    val zoneToken: String,
    val refreshToken: String,
    val apiToken: String
  )

  private fun signedIn(role: Role): SignedIn {
    val registration = register(VirtualAuthenticator())
    val body = mapper.readTree(rawExchange(registration.code, registration.verifier).body)
    val zoneToken = body.get("token").asText()
    val accountId = accountIdOf(zoneToken)
    committed { jdbc.update("UPDATE account SET role = ? WHERE id = ?", role.name, accountId) }

    return SignedIn(accountId, zoneToken, body.get("refresh_token").asText(), body.get("api_token").asText())
  }

  private fun admin(action: String, actor: SignedIn, targetId: Long, body: Map<String, Any>): ResponseEntity<String> {
    val headers = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_JSON
      setBearerAuth(actor.apiToken)
    }

    return restTemplate.postForEntity("/api/v1/admin/accounts/$targetId/$action", HttpEntity(body, headers), String::class.java)
  }

  /** The scenario runs in a test transaction the server cannot see into, so writes it must see are committed. */
  private fun committed(block: () -> Unit) {
    val template = TransactionTemplate(transactionManager)
    template.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    template.executeWithoutResult { block() }
  }
}

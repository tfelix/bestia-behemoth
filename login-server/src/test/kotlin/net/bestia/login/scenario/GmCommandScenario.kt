package net.bestia.login.scenario

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.account.Role
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply.Outcome
import net.bestia.bus.valkey.ValkeyLoginBus
import net.bestia.bus.valkey.ValkeyLoginCommandServer
import net.bestia.login.webauthn.VirtualAuthenticator
import net.bestia.login.zone.KickStream
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import java.time.Duration
import java.util.concurrent.TimeUnit

/** GMs ban and kick players from the companion app: the api-server sends the command, the login server acts. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GmCommandScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var kicks: KickStream

  @Autowired
  private lateinit var jdbc: JdbcTemplate

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Autowired
  private lateinit var valkeyContainer: GenericContainer<*>

  @Autowired
  private lateinit var commandServer: ValkeyLoginCommandServer

  private lateinit var apiClient: RedisClient
  private lateinit var bus: ValkeyLoginBus

  @BeforeAll
  fun connectAsApiServer() {
    val uri = RedisURI.Builder.redis(valkeyContainer.host, valkeyContainer.getMappedPort(6379))
      .withAuthentication("api", "api-dev-password".toCharArray())
      .build()
    apiClient = RedisClient.create(uri)
    bus = ValkeyLoginBus(apiClient, instanceId = "gm-scenario")
    await().atMost(Duration.ofSeconds(10)).until { commandServer.isListening }
  }

  @AfterAll
  fun disconnect() {
    bus.close()
    apiClient.shutdown()
  }

  @Test
  fun `a GM bans a player, who can no longer resume`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    val outcome = send(gm, LoginCommand.Ban(player.accountId, "botting", durationHours = 24))

    assertEquals(Outcome.DONE, outcome)
    // 400, not 403: the ban revoked the refresh token before any account check could see the ban.
    assertEquals(400, rawRefresh(player.refreshToken).statusCode.value())
    assertEquals(listOf("BANNED"), kicks.ordersFor(player.accountId).map { it.reason })
  }

  @Test
  fun `a GM kicks a player from the zones, and the kick is written down`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    assertEquals(Outcome.DONE, send(gm, LoginCommand.Kick(player.accountId, "stuck")))

    assertEquals(listOf("GM_KICK"), kicks.ordersFor(player.accountId).map { it.reason })
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

    assertEquals(Outcome.NOT_ALLOWED, send(player, LoginCommand.Ban(other.accountId, "x", durationHours = null)))
    assertEquals(emptyList<String>(), kicks.ordersFor(other.accountId).map { it.reason })
  }

  /** The command only names the GM. What it may do is read again on every command. */
  @Test
  fun `a GM who was banned can no longer ban`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)
    committed { jdbc.update("UPDATE account SET status = 'PERMA_BANNED' WHERE id = ?", gm.accountId) }

    assertEquals(Outcome.NOT_ALLOWED, send(gm, LoginCommand.Ban(player.accountId, "x", durationHours = null)))
  }

  @Test
  fun `a GM is told when the account does not exist`() {
    val gm = signedIn(Role.GM)

    assertEquals(Outcome.NO_SUCH_ACCOUNT, send(gm, LoginCommand.Kick(Long.MAX_VALUE, "who")))
  }

  @Test
  fun `a blank note or an endless timed ban is refused`() {
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    assertEquals(Outcome.INVALID, send(gm, LoginCommand.Kick(player.accountId, "   ")))
    assertEquals(Outcome.INVALID, send(gm, LoginCommand.Ban(player.accountId, "x", durationHours = Long.MAX_VALUE)))
  }

  @Test
  fun `a GM ban and kick are counted with the kick orders they publish`() {
    val banned = watch("login.gm.action", "action", "ban", "result", "ok")
    val kicked = watch("login.gm.action", "action", "kick", "result", "ok")
    val banKick = watch("login.kick.published", "reason", "BANNED", "result", "ok")
    val gmKick = watch("login.kick.published", "reason", "GM_KICK", "result", "ok")
    val gm = signedIn(Role.GM)
    val player = signedIn(Role.USER)

    send(gm, LoginCommand.Kick(player.accountId, "stuck"))
    send(gm, LoginCommand.Ban(player.accountId, "botting", durationHours = 24))

    assertEquals(1.0, kicked.moved())
    assertEquals(1.0, banned.moved())
    assertEquals(1.0, gmKick.moved())
    assertEquals(1.0, banKick.moved())
  }

  @Test
  fun `a refused GM action is counted as refused`() {
    val refused = watch("login.gm.action", "action", "ban", "result", "refused")
    val player = signedIn(Role.USER)
    val other = signedIn(Role.USER)

    send(player, LoginCommand.Ban(other.accountId, "x", durationHours = null))

    assertEquals(1.0, refused.moved())
  }

  private fun send(actor: SignedIn, command: LoginCommand): Outcome {
    return bus.send(actor.accountId, command).get(10, TimeUnit.SECONDS)
  }

  private data class SignedIn(
    val accountId: Long,
    val refreshToken: String
  )

  private fun signedIn(role: Role): SignedIn {
    val registration = register(VirtualAuthenticator())
    val body = mapper.readTree(rawExchange(registration.code, registration.verifier).body)
    val accountId = accountIdOf(body.get("token").asText())
    committed { jdbc.update("UPDATE account SET role = ? WHERE id = ?", role.name, accountId) }

    return SignedIn(accountId, body.get("refresh_token").asText())
  }

  /** The scenario runs in a test transaction the server cannot see into, so writes it must see are committed. */
  private fun committed(block: () -> Unit) {
    val template = TransactionTemplate(transactionManager)
    template.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    template.executeWithoutResult { block() }
  }
}

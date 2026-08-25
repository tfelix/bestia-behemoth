package net.bestia.login.gamelogin

import net.bestia.login.account.Account
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.AccountStatus
import net.bestia.login.ratelimit.RateLimiter
import net.bestia.login.scenario.BaseLoginScenario
import net.bestia.login.util.SecureTokens
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import java.time.LocalDateTime

/**
 * The row-level rules the end-to-end scenario cannot reach.
 *
 * Driven in process rather than over HTTP for two reasons: a token has to be planted with an expiry
 * in the past, and a ban has to be visible to the code that reads it - both of which need the test
 * and the server to share one transaction, which [net.bestia.login.scenario.SessionResumeScenario]
 * by definition does not. Extends the scenario base purely to share its Spring context, so this does
 * not stand up a second database container.
 */
class RefreshTokenServiceTest : BaseLoginScenario() {

  @Autowired
  private lateinit var refreshTokenService: RefreshTokenService

  @Autowired
  private lateinit var tokens: RefreshTokenRepository

  @Autowired
  private lateinit var accounts: AccountRepository

  @Autowired
  private lateinit var gameLoginController: GameLoginController

  @Autowired
  private lateinit var rateLimiter: RateLimiter

  @BeforeEach
  fun resetLimits() {
    rateLimiter.reset()
  }

  @Test
  fun `rotation issues a successor and retires its predecessor`() {
    val accountId = newAccount().id
    val first = refreshTokenService.issueForNewSession(accountId)

    val rotated = assertRotated(first)

    assertEquals(accountId, rotated.accountId)
    assertNotEquals(first, rotated.token)
    assertInstanceOf(
      RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(first),
      "the predecessor must be spent"
    )
  }

  @Test
  fun `a successor stays in the family of the token it replaced`() {
    val accountId = newAccount().id
    val first = refreshTokenService.issueForNewSession(accountId)
    val second = assertRotated(first).token

    assertEquals(
      familyOf(first), familyOf(second),
      "a successor in a new family would make the chain unrevocable as a unit"
    )
  }

  @Test
  fun `only the newest token in a chain counts as usable`() {
    val accountId = newAccount().id

    refreshTokenService.issueForNewSession(accountId).let { assertRotated(it) }

    assertEquals(1, refreshTokenService.usableCountFor(accountId))
  }

  @Test
  fun `a token presented after it was spent takes its family down`() {
    val accountId = newAccount().id
    val first = refreshTokenService.issueForNewSession(accountId)
    val second = assertRotated(first).token

    refreshTokenService.rotate(first)

    assertInstanceOf(RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(second))
    assertEquals(0, refreshTokenService.usableCountFor(accountId), "the whole chain must be gone")
  }

  /** Revocation must not reach past the family, or one stolen token would sign the player out everywhere. */
  @Test
  fun `a replay in one family leaves another family of the same account alone`() {
    val accountId = newAccount().id
    val desktop = refreshTokenService.issueForNewSession(accountId)
    val laptop = refreshTokenService.issueForNewSession(accountId)

    val desktopSuccessor = assertRotated(desktop).token
    refreshTokenService.rotate(desktop)

    assertInstanceOf(RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(desktopSuccessor))
    assertInstanceOf(RefreshTokenService.Rotation.Rotated::class.java, refreshTokenService.rotate(laptop))
  }

  @Test
  fun `an expired token is refused`() {
    val accountId = newAccount().id
    val stale = plantToken(accountId, expiresAt = LocalDateTime.now().minusMinutes(1))

    assertInstanceOf(RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(stale))
  }

  @Test
  fun `revoking an account ends every one of its sessions`() {
    val accountId = newAccount().id
    val desktop = refreshTokenService.issueForNewSession(accountId)
    val laptop = refreshTokenService.issueForNewSession(accountId)

    assertEquals(2, refreshTokenService.revokeAllForAccount(accountId))

    assertInstanceOf(RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(desktop))
    assertInstanceOf(RefreshTokenService.Rotation.Refused::class.java, refreshTokenService.rotate(laptop))
  }

  /**
   * Nothing about the passkey ceremony is repeated on a resume, so the ban that was applied since has
   * to be caught here or a banned player simply keeps playing until their token runs out.
   */
  @Test
  fun `a banned account cannot resume and loses its standing sessions`() {
    val account = newAccount()
    val stored = refreshTokenService.issueForNewSession(account.id)

    account.status = AccountStatus.PERMA_BANNED
    accounts.save(account)

    val response = gameLoginController.refresh(
      GameLoginController.RefreshRequest(refreshToken = stored),
      MockHttpServletRequest()
    )

    assertEquals(403, response.statusCode.value())
    assertEquals(
      0, refreshTokenService.usableCountFor(account.id),
      "the successor minted before the guard ran must not be left behind"
    )
  }

  @Test
  fun `the sweeper only removes tokens that can no longer be presented`() {
    val accountId = newAccount().id
    val stale = plantToken(accountId, expiresAt = LocalDateTime.now().minusDays(1))
    val live = refreshTokenService.issueForNewSession(accountId)

    refreshTokenService.sweepExpired()

    assertFalse(tokens.existsById(hash(stale)))
    assertTrue(tokens.existsById(hash(live)))
  }

  private fun assertRotated(rawToken: String): RefreshTokenService.Rotation.Rotated {
    return assertInstanceOf(
      RefreshTokenService.Rotation.Rotated::class.java,
      refreshTokenService.rotate(rawToken)
    )
  }

  private fun newAccount(): Account {
    return accounts.save(Account())
  }

  /** A row the service would never write, so that the expiry and sweep branches can be reached. */
  private fun plantToken(accountId: Long, expiresAt: LocalDateTime): String {
    val token = SecureTokens.randomToken()

    tokens.save(
      RefreshToken(
        tokenHash = hash(token),
        accountId = accountId,
        familyId = SecureTokens.randomToken(16),
        expiresAt = expiresAt
      )
    )

    return token
  }

  private fun familyOf(rawToken: String): String {
    return tokens.findById(hash(rawToken)).orElseThrow().familyId
  }

  private fun hash(rawToken: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(rawToken))
  }
}

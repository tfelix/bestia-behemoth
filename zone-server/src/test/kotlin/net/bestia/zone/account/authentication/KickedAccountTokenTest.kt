package net.bestia.zone.account.authentication

import io.mockk.every
import io.mockk.mockk
import net.bestia.account.Role
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.config.ZoneConfig
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.bestia.zone.session.AuthenticationProcessor

/**
 * A kicked player may still hold a login token the login server issued a moment before the ban. Reconnecting
 * with it must not undo the kick.
 */
class KickedAccountTokenTest {

  private val kickedAccounts = KickedAccounts(
    ZoneConfig(bestiaBaseSlotCount = 1, bestiaMaxSlotCount = 1, jwtAuthSecretKey = "", shardId = 1)
  )

  @Test
  fun `a token issued before the kick is refused`() {
    kickedAccounts.remember(ACCOUNT, System.currentTimeMillis())

    val result = processorAccepting(issuedAt = Date(System.currentTimeMillis() - 10_000)).authenticate(envelope())

    assertEquals(AuthenticationProcessor.AuthenticationFailed, result)
  }

  @Test
  fun `a token issued after the kick is accepted`() {
    kickedAccounts.remember(ACCOUNT, System.currentTimeMillis())

    val result = processorAccepting(issuedAt = Date(System.currentTimeMillis() + 5_000)).authenticate(envelope())

    assertIs<AuthenticationProcessor.AuthenticationSuccess>(result)
  }

  @Test
  fun `a kick read late is judged by when it was made, not when it arrived`() {
    val kickedAt = System.currentTimeMillis() - 60_000
    kickedAccounts.remember(ACCOUNT, kickedAt)

    val before = processorAccepting(issuedAt = Date(kickedAt - 1_000)).authenticate(envelope())
    val after = processorAccepting(issuedAt = Date(kickedAt + 1_000)).authenticate(envelope())

    assertEquals(AuthenticationProcessor.AuthenticationFailed, before)
    assertIs<AuthenticationProcessor.AuthenticationSuccess>(after)
  }

  @Test
  fun `a kick older than the zone remembers is not kept`() {
    kickedAccounts.remember(ACCOUNT, System.currentTimeMillis() - 301_000)

    val result = processorAccepting(issuedAt = Date(System.currentTimeMillis() - 400_000)).authenticate(envelope())

    assertIs<AuthenticationProcessor.AuthenticationSuccess>(result)
  }

  @Test
  fun `an earlier kick read after a later one does not shorten it`() {
    val later = System.currentTimeMillis() - 10_000
    kickedAccounts.remember(ACCOUNT, later)
    kickedAccounts.remember(ACCOUNT, later - 50_000)

    val result = processorAccepting(issuedAt = Date(later - 20_000)).authenticate(envelope())

    assertEquals(AuthenticationProcessor.AuthenticationFailed, result)
  }

  private fun processorAccepting(issuedAt: Date): JwtAuthenticationProcessor {
    val validator = mockk<LoginTokenValidator> {
      every { validateLoginToken(TOKEN) } returns
        LoginTokenValidator.LoginTokenClaims(ACCOUNT, Role.USER, emptySet(), issuedAt)
    }

    return JwtAuthenticationProcessor(validator, kickedAccounts)
  }

  private fun envelope(): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken(TOKEN))
      .build()
  }

  private companion object {
    const val ACCOUNT = 1L
    const val TOKEN = "token"
  }
}

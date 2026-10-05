package net.bestia.zone.account.authentication

import io.mockk.every
import io.mockk.mockk
import net.bestia.account.Role
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.ZoneConfig
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
    kickedAccounts.remember(ACCOUNT)

    val result = processorAccepting(issuedAt = Date(System.currentTimeMillis() - 10_000)).authenticate(envelope())

    assertEquals(AuthenticationProcessor.AuthenticationFailed, result)
  }

  @Test
  fun `a token issued after the kick is accepted`() {
    kickedAccounts.remember(ACCOUNT)

    val result = processorAccepting(issuedAt = Date(System.currentTimeMillis() + 5_000)).authenticate(envelope())

    assertIs<AuthenticationProcessor.AuthenticationSuccess>(result)
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

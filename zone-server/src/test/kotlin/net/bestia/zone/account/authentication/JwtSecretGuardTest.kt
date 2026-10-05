package net.bestia.zone.account.authentication

import net.bestia.zone.ZoneConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

/**
 * The development secret is committed, so a server running with it accepts tokens anybody can sign.
 */
class JwtSecretGuardTest {

  @Test
  fun `the development secret is refused outside development`() {
    assertThrows<IllegalStateException> { JwtSecretGuard(config(DEV_SECRET), MockEnvironment()) }
  }

  @Test
  fun `the development secret is accepted in development and in tests`() {
    assertDoesNotThrow { JwtSecretGuard(config(DEV_SECRET), MockEnvironment().apply { setActiveProfiles("dev") }) }
    assertDoesNotThrow { JwtSecretGuard(config(DEV_SECRET), MockEnvironment().apply { setActiveProfiles("test") }) }
  }

  @Test
  fun `a deployment secret is accepted anywhere`() {
    assertDoesNotThrow { JwtSecretGuard(config("a-deployment-secret-nobody-has-seen-before"), MockEnvironment()) }
  }

  private fun config(secret: String): ZoneConfig {
    return ZoneConfig(bestiaBaseSlotCount = 4, bestiaMaxSlotCount = 8, jwtAuthSecretKey = secret, shardId = 1)
  }

  private companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"
  }
}

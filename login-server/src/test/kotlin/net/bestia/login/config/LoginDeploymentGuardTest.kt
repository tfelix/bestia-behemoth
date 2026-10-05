package net.bestia.login.config

import net.bestia.account.Role
import net.bestia.login.account.AccountConfig
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.webauthn.WebAuthnConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

/**
 * Settings that are only safe on a developer's own machine refuse to boot anywhere else.
 */
class LoginDeploymentGuardTest {

  @Test
  fun `a raised sign-up role is refused outside development`() {
    assertThrows<IllegalStateException> { guard(signUpRole = Role.SUPER_GM) }
  }

  @Test
  fun `the development secret and a short secret are refused outside development`() {
    assertThrows<IllegalStateException> { guard(secret = DEV_SECRET) }
    assertThrows<IllegalStateException> { guard(secret = "too-short") }
  }

  @Test
  fun `relaxed origin ports are refused outside development`() {
    assertThrows<IllegalStateException> { guard(allowOriginPort = true) }
  }

  @Test
  fun `development settings are accepted under the dev profile`() {
    assertDoesNotThrow {
      guard(signUpRole = Role.SUPER_GM, secret = DEV_SECRET, allowOriginPort = true, profile = "dev")
    }
  }

  @Test
  fun `a deployment configuration is accepted`() {
    assertDoesNotThrow { guard() }
  }

  private fun guard(
    signUpRole: Role = Role.USER,
    secret: String = "a-deployment-secret-nobody-has-seen-before",
    allowOriginPort: Boolean = false,
    profile: String? = null
  ): LoginDeploymentGuard {
    return LoginDeploymentGuard(
      AccountConfig(signUpRole),
      JwtConfig(secret = secret, loginTokenMinutes = 2),
      WebAuthnConfig(
        rpId = "bestia.game",
        rpName = "Bestia",
        origins = listOf("https://auth.bestia.game"),
        allowOriginPort = allowOriginPort
      ),
      MockEnvironment().apply { profile?.let { setActiveProfiles(it) } }
    )
  }

  private companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"
  }
}

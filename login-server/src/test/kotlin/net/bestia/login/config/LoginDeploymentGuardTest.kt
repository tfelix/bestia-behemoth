package net.bestia.login.config

import net.bestia.account.Role
import net.bestia.login.account.AccountConfig
import net.bestia.login.gamelogin.CompanionLoginConfig
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.webauthn.WebAuthnConfig
import net.bestia.login.zone.BusConfig
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
  fun `the development bus password is refused outside development`() {
    assertThrows<IllegalStateException> { guard(busPassword = LoginDeploymentGuard.DEV_BUS_PASSWORD) }
  }

  @Test
  fun `development settings are accepted under the dev profile`() {
    assertDoesNotThrow {
      guard(
        signUpRole = Role.SUPER_GM,
        secret = DEV_SECRET,
        allowOriginPort = true,
        busPassword = LoginDeploymentGuard.DEV_BUS_PASSWORD,
        profile = "dev",
      )
    }
  }

  @Test
  fun `a deployment configuration is accepted`() {
    assertDoesNotThrow { guard() }
    assertDoesNotThrow { guard(companionSecret = "another-secret-only-the-api-server-holds") }
  }

  /** The api-server holds the companion key; if it were the zone's, it could mint zone tokens. */
  @Test
  fun `with companion login on, its key must be its own and not the development one`() {
    assertThrows<IllegalStateException> { guard(companionSecret = LoginDeploymentGuard.DEV_COMPANION_SECRET) }
    assertThrows<IllegalStateException> { guard(companionSecret = "too-short") }
    assertThrows<IllegalStateException> { guard(companionSecret = "a-deployment-secret-nobody-has-seen-before") }
  }

  private fun guard(
    signUpRole: Role = Role.USER,
    secret: String = "a-deployment-secret-nobody-has-seen-before",
    allowOriginPort: Boolean = false,
    profile: String? = null,
    companionSecret: String? = null,
    busPassword: String = "a-deployment-password",
  ): LoginDeploymentGuard {
    val companion = companionSecret?.let { CompanionLoginConfig(redirectUris = listOf("https://app.bestia.game/auth/callback")) }

    return LoginDeploymentGuard(
      AccountConfig(signUpRole),
      JwtConfig(secret = secret, loginTokenMinutes = 2, companionSecret = companionSecret.orEmpty()),
      WebAuthnConfig(
        rpId = "bestia.game",
        rpName = "Bestia",
        origins = listOf("https://auth.bestia.game"),
        allowOriginPort = allowOriginPort
      ),
      companion ?: CompanionLoginConfig(),
      BusConfig(host = "valkey", username = "login", password = busPassword),
      MockEnvironment().apply { profile?.let { setActiveProfiles(it) } }
    )
  }

  private companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"
  }
}

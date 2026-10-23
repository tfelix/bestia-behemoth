package net.bestia.login.config

import net.bestia.account.Role
import net.bestia.login.account.AccountConfig
import net.bestia.login.gamelogin.CompanionLoginConfig
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.webauthn.WebAuthnConfig
import net.bestia.login.zone.BusConfig
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * Refuses to boot a deployment with settings that are only safe on a developer's own machine. Checked at
 * startup rather than on first use, so a misconfigured server never takes a single request.
 */
@Component
class LoginDeploymentGuard(
  accountConfig: AccountConfig,
  jwtConfig: JwtConfig,
  webAuthnConfig: WebAuthnConfig,
  companionLoginConfig: CompanionLoginConfig,
  busConfig: BusConfig,
  environment: Environment
) {

  init {
    if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
      check(accountConfig.signUpRole == Role.USER) {
        "account.sign-up-role ${accountConfig.signUpRole} would hand elevated rights to everyone who registers"
      }
      check(jwtConfig.secret != DEV_SECRET) { "jwt.secret is the committed development secret; set JWT_SECRET" }
      check(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8).size >= MIN_SECRET_BYTES) {
        "jwt.secret must be at least $MIN_SECRET_BYTES bytes"
      }
      check(!webAuthnConfig.allowOriginPort) { "webauthn.allow-origin-port is only correct for local development" }
      check(busConfig.password != DEV_BUS_PASSWORD) { "bus.password is the committed development password" }
      if (companionLoginConfig.redirectUris.isNotEmpty()) {
        check(jwtConfig.companionSecret != DEV_COMPANION_SECRET) {
          "jwt.companion-secret is the committed development secret; set JWT_COMPANION_SECRET"
        }
        check(jwtConfig.companionSecret.toByteArray(StandardCharsets.UTF_8).size >= MIN_SECRET_BYTES) {
          "jwt.companion-secret must be at least $MIN_SECRET_BYTES bytes"
        }
        check(jwtConfig.companionSecret != jwtConfig.secret) {
          "jwt.companion-secret must differ from jwt.secret, or the api-server could mint zone tokens"
        }
      }
    }
  }

  companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"
    const val DEV_COMPANION_SECRET = "dev-companion-secret-shared-with-api-server"
    const val DEV_BUS_PASSWORD = "login-dev-password"

    /** HMAC-SHA256 needs a key at least as long as its output. */
    private const val MIN_SECRET_BYTES = 32
  }
}

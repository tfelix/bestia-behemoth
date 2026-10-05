package net.bestia.login.config

import net.bestia.account.Role
import net.bestia.login.account.AccountConfig
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.webauthn.WebAuthnConfig
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
    }
  }

  companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"

    /** HMAC-SHA256 needs a key at least as long as its output. */
    private const val MIN_SECRET_BYTES = 32
  }
}

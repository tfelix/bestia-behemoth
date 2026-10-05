package net.bestia.zone.account.authentication

import net.bestia.zone.ZoneConfig
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component

/**
 * Refuses to boot a deployment with the committed development secret: anybody could sign a token the zone
 * accepts, for any account and any role.
 */
@Component
class JwtSecretGuard(
  config: ZoneConfig,
  environment: Environment
) {

  init {
    val development = environment.acceptsProfiles(Profiles.of("dev", "test"))

    check(development || config.jwtAuthSecretKey != DEV_SECRET) {
      "zone.jwt-auth-secret-key is the committed development secret; set ZONE_JWT_AUTH_SECRET_KEY"
    }
  }

  companion object {
    const val DEV_SECRET = "your-secret-key-here-change-in-production"
  }
}

package net.bestia.api.config

import net.bestia.api.auth.CompanionProperties
import net.bestia.api.push.PushProperties
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/** Refuses to boot outside development with the keys committed for development. */
@Component
class ApiDeploymentGuard(
  companion: CompanionProperties,
  push: PushProperties,
  environment: Environment,
) {

  init {
    if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
      check(companion.jwtSecret != DEV_COMPANION_SECRET) {
        "companion.jwt-secret is the committed development secret; set COMPANION_JWT_SECRET"
      }
      check(companion.jwtSecret.toByteArray(StandardCharsets.UTF_8).size >= MIN_SECRET_BYTES) {
        "companion.jwt-secret must be at least $MIN_SECRET_BYTES bytes"
      }
      check(push.vapidPublicKey != DEV_VAPID_PUBLIC_KEY) {
        "push.vapid-public-key is the committed development key; set PUSH_VAPID_PUBLIC_KEY and PUSH_VAPID_PRIVATE_KEY"
      }
    }
  }

  companion object {
    const val DEV_COMPANION_SECRET = "dev-companion-secret-shared-with-api-service"
    const val DEV_VAPID_PUBLIC_KEY = "BPC8EVmzvqSQvPRBDi7FjAQqyAJGZsxH9jKT16aTDbUK79Vg4h1rwiPXhadB5_X6i1fOj0--Na1QGZJdQgNlfko"

    /** HMAC-SHA256 needs a key at least as long as its output. */
    private const val MIN_SECRET_BYTES = 32
  }
}

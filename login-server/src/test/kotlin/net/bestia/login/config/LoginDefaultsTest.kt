package net.bestia.login.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

/**
 * What the shipped base config holds. A server with no profile set runs as `dev`; one given any other profile
 * gets none of the dev settings, which live in `application-dev.yml` only.
 */
class LoginDefaultsTest {

  private val shipped: Properties = YamlPropertiesFactoryBean()
    .apply { setResources(ClassPathResource("application.yml")) }
    .getObject()!!

  /** A default, not an active profile: a deployment that sets its own profile drops dev entirely. */
  @Test
  fun `the server runs as dev unless a profile is set`() {
    assertEquals("dev", shipped.getProperty("spring.profiles.default"))
    assertNull(shipped.getProperty("spring.profiles.active"))
  }

  @Test
  fun `the packaged server signs nobody up above USER`() {
    assertEquals("USER", shipped.getProperty("account.sign-up-role"))
  }

  /** Missing, a deployment with its own profile that forgets them does not boot. */
  @Test
  fun `the packaged server carries no secret and no localhost relying party`() {
    assertNull(shipped.getProperty("jwt.secret"))
    assertNull(shipped.getProperty("webauthn.rp-id"))
    assertNull(shipped.getProperty("webauthn.allow-origin-port"))
  }
}

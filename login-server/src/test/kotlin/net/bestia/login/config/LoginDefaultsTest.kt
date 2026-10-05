package net.bestia.login.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

/**
 * What a packaged login server runs with when nobody configured it, which is what a careless deployment gets.
 */
class LoginDefaultsTest {

  private val shipped: Properties = YamlPropertiesFactoryBean()
    .apply { setResources(ClassPathResource("application.yml")) }
    .getObject()!!

  @Test
  fun `the packaged server does not run as dev`() {
    assertNull(shipped.getProperty("spring.profiles.active"))
  }

  @Test
  fun `the packaged server signs nobody up above USER`() {
    assertEquals("USER", shipped.getProperty("account.sign-up-role"))
  }

  /** Missing, a deployment that forgets them does not boot rather than running on public or localhost values. */
  @Test
  fun `the packaged server carries no secret and no localhost relying party`() {
    assertNull(shipped.getProperty("jwt.secret"))
    assertNull(shipped.getProperty("webauthn.rp-id"))
    assertNull(shipped.getProperty("webauthn.allow-origin-port"))
  }
}

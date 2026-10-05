package net.bestia.zone

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the shipped base config holds. A server with no profile set runs as `dev`; one given any other profile
 * gets none of the dev settings, which live in `application-dev.yml` only.
 */
class ZoneDefaultConfigTest {

  /** A default, not an active profile: a deployment that sets its own profile drops dev entirely. */
  @Test
  fun `the server runs as dev unless a profile is set`() {
    assertEquals("dev", load("application.yml").getProperty("spring.profiles.default"))
    assertNull(load("application.yml").getProperty("spring.profiles.active"))
  }

  @Test
  fun `the shipped config logs at INFO and only the dev profile turns on TRACE`() {
    assertEquals("INFO", load("application.yml").getProperty("logging.level.net.bestia.zone"))
    assertEquals("TRACE", load("application-dev.yml").getProperty("logging.level.net.bestia.zone"))
  }

  /** Missing, a server with its own profile refuses to boot rather than accepting tokens signed with a public value. */
  @Test
  fun `the shipped config carries no JWT secret`() {
    assertNull(load("application.yml").getProperty("zone.jwt-auth-secret-key"))
  }

  private fun load(name: String): Properties {
    val resource = ClassPathResource(name)
    if (!resource.exists()) {
      return Properties()
    }

    val yaml = YamlPropertiesFactoryBean()
    yaml.setResources(resource)

    return yaml.getObject()!!
  }
}

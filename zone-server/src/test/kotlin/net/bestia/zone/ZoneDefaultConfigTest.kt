package net.bestia.zone

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

  /** The metrics show what the zone does; they belong on loopback, apart from the tiles and the game socket. */
  @Test
  fun `metrics are served on a loopback port of their own, and nothing else is exposed`() {
    val config = load("application.yml")
    val port = config.getProperty("management.server.port")

    assertEquals("127.0.0.1", config.getProperty("management.server.address"))
    assertNotEquals(config.getProperty("server.port"), port)
    assertNotEquals(config.getProperty("socket.port"), port)
    assertEquals("health,prometheus", config.getProperty("management.endpoints.web.exposure.include"))
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

package net.bestia.zone

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties
import kotlin.test.assertEquals

/**
 * What a packaged zone server runs with when nobody configured it, which is what a careless deployment gets.
 */
class ZoneDefaultConfigTest {

  @Test
  fun `the shipped config logs at INFO and only the dev profile turns on TRACE`() {
    assertEquals("INFO", load("application.yml").getProperty("logging.level.net.bestia.zone"))
    assertEquals("TRACE", load("application-dev.yml").getProperty("logging.level.net.bestia.zone"))
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

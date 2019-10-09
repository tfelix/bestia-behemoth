package net.bestia.zoneserver

import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
@Tag("smoke")
class BootableSmokeTest {

  // Currently the Spring Context is not loaded because of shitty Intellij/Spring
  @Autowired
  lateinit var applicationContext: ApplicationContext

  @DisplayName("Startup Application Ctx")
  @Test
  fun injectionTests() {
    println(applicationContext.toString())
    Assert.assertFalse(true)
  }
}
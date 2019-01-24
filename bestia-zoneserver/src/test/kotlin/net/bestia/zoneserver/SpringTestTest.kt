package net.bestia.zoneserver

import org.junit.Assert
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Playground for testing SpringBootTests
 */
@SpringBootTest
class SpringTestTest {

  // Currently the Spring Context is not loaded because of shitty Intellij/Spring
  @Autowired
  lateinit var applicationContext: ApplicationContext

  @Test
  fun injectionTests() {
    println(applicationContext.toString())
    Assert.assertFalse(true)
  }
}
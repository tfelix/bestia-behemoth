package net.bestia.zoneserver

import org.junit.Assert
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
@Tag("it")
class BootableTest {

  // Currently the Spring Context is not loaded because of shitty Intellij/Spring
  @Autowired
  lateinit var applicationContext: ApplicationContext

  @Test
  fun injectionTests() {
    println(applicationContext.toString())
    Assert.assertFalse(true)
  }
}
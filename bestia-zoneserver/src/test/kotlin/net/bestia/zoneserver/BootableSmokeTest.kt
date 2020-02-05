package net.bestia.zoneserver

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
@Tag("smoke")
class BootableSmokeTest {

  @DisplayName("Startup Application Ctx")
  @Test
  fun injectionTests() {
    println("works")
    assertFalse(true)
  }
}
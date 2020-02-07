package net.bestia.zoneserver.it

import net.bestia.messages.AuthProtos
import net.bestia.zoneserver.account.LoginService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.test.context.ActiveProfiles

class AllAuthenticatingLoginService : LoginService {
  override fun isLoginAllowedForAccount(accountId: Long): Boolean {
    return true
  }
}

@SpringBootTest
// @ActiveProfiles(profiles = ["test"])
@Tag("smoke")
class BootableSmokeTest {

  @TestConfiguration
  class SmokeTestConfig {

    @Bean
    fun allAuthenticatingLoginService(): LoginService {
      return AllAuthenticatingLoginService()
    }
  }

  @DisplayName("Startup Application Ctx")
  @Test
  fun injectionTests() {
    // Setup an auth message.
    val msg = AuthProtos.Auth.newBuilder().apply {
      accountId = 1
      token = "test123"
    }.build()



    assertFalse(true)
  }
}
package net.bestia.zoneserver.it

import net.bestia.messages.AuthMessageProto
import net.bestia.zoneserver.RxTxSocket
import net.bestia.zoneserver.account.LoginService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

class AllAuthenticatingLoginService : LoginService {
  override fun isLoginAllowedForAccount(accountId: Long): Boolean {
    return true
  }
}

@SpringBootTest
// @ActiveProfiles(profiles = ["test"])
@Tag("smoke")
class BootableSmokeTest {

  private lateinit var socket: RxTxSocket

  @TestConfiguration
  class SmokeTestConfig {

    @Bean
    fun allAuthenticatingLoginService(): LoginService {
      return AllAuthenticatingLoginService()
    }
  }

  @BeforeEach
  fun setup() {

  }

  @DisplayName("Startup Application Ctx")
  @Test
  fun injectionTests() {
    Thread.sleep(5000)
    socket = RxTxSocket("127.0.0.1", 8990)
    // Setup an auth message.
    val msg = AuthMessageProto.AuthMessage.newBuilder()
        .setAccountId(1)
        .setToken("50cb5740-c390-4d48-932f-eef7cbc113c1")
        .build()
        .toByteArray()

    socket.send(msg)

    Thread.sleep(5000)

    socket.close()
    socket.join()
    assertFalse(true)
  }
}
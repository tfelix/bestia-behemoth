package net.bestia.zoneserver

import net.bestia.messages.proto.AuthProto
import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@SpringBootTest
// @ActiveProfiles(profiles = ["test"])
@Tag("smoke")
class BootableSmokeTest {

  private lateinit var socket: RxTxSocket

  private class AllAuthenticatingLoginService : LoginCheck {
    override fun isLoginAllowedForAccount(accountId: Long, token: String): LoginResponse {
      return LoginResponse.SUCCESS
    }
  }

  @TestConfiguration
  class SmokeTestConfig {

    @Bean
    fun allAuthenticatingLoginService(): LoginCheck {
      return AllAuthenticatingLoginService()
    }
  }

  @BeforeEach
  fun setup() {

  }

  @DisplayName("Client can login to server")
  @Test
  fun simpleLogin() {
    Thread.sleep(5000)
    socket = RxTxSocket("127.0.0.1", 8990)
    // Setup an auth message.
    val msg = AuthProto.Auth.newBuilder()
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
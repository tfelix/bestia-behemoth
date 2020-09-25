package net.bestia.zoneserver

import net.bestia.messages.proto.AccountProtos
import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@IntegrationTest
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

  @DisplayName("Client can login to server")
  @Test
  fun simpleLogin() {
    Thread.sleep(5000)
    socket = RxTxSocket("127.0.0.1", 8990)
    // Setup an auth message.
    val msg = AccountProtos.AuthRequest.newBuilder()
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
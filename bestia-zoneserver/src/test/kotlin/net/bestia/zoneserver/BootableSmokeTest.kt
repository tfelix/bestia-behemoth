package net.bestia.zoneserver

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.ChatProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.junit.Assert
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@IntegrationTest
class BootableSmokeTest {

  private val socket = ClientSocket("127.0.0.1", 8990)

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
    socket.connect()
    // Request and check for bestia overview message

    // Send chat message

    // Set and request client vars

    // Move player bestia and await component updates

    // Logout

    val chatPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
        ChatProtos.ChatRequest.newBuilder()
            .setAccountId(1)
            .setMode(ChatProtos.ChatMode.PUBLIC)
            .setText("Hello World1234")
            .build()
    ).build().toByteArray()

    socket.send(chatPayload)
    val response = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)

    Assert.assertNotNull(response)

    socket.close()
  }
}
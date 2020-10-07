package net.bestia.zoneserver

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.ChatProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.socket.LoginResponse
import org.junit.Assert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@IntegrationTest
class BootableSmokeTest {

  private val socket = ClientSocket("127.0.0.1", 8990)

  private val chatPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setAccountId(1)
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText("Hello World1234")
          .build()
  ).build().toByteArray()

  private val clientVarRequestSetValue = MessageProtos.Wrapper.newBuilder().setClientVarRequest(
      AccountProtos.ClientVarRequest.newBuilder()
          .setKey("testkey")
          .setValueToSet("newvalue")
          .build()
  ).build().toByteArray()

  private val clientVarRequestValue = MessageProtos.Wrapper.newBuilder().setClientVarRequest(
      AccountProtos.ClientVarRequest.newBuilder()
          .setKey("testkey")
          .build()
  ).build().toByteArray()

  private val clientInfoRequest = MessageProtos.Wrapper.newBuilder().setClientInfoRequest(
      AccountProtos.ClientInfoRequest.newBuilder().build()
  ).build().toByteArray()


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
    // socket.send(chatPayload)
    // val resp1 = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)
    // Assert.assertNotNull(resp1)

    // Set and request client vars
    socket.send(clientVarRequestSetValue)
    val resp2 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
    Assert.assertNotNull(resp2)
    socket.send(clientVarRequestValue)
    val resp3 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
    Assert.assertNotNull(resp3)

    // Send client info request
    socket.send(clientInfoRequest)
    val resp4 = socket.receive<AccountProtos.ClientInfoResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE)
    println(resp4)
    // Move player bestia and await component updates

    // Logout

    socket.close()
  }
}
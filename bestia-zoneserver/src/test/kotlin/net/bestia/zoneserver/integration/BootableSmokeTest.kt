package net.bestia.zoneserver.integration

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.ChatProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.ClientSocket
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.util.*

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

  private val chatMapMoveCommandPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setAccountId(1)
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText("/mm 10 10")
          .build()
  ).build().toByteArray()

  private val chatServerCommandPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setAccountId(1)
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText("/serverinfo")
          .build()
  ).build().toByteArray()

  private val randomVarValue = UUID.randomUUID().toString().take(5)

  private val clientVarRequestSetValue = MessageProtos.Wrapper.newBuilder().setClientVarRequest(
      AccountProtos.ClientVarRequest.newBuilder()
          .setKey("testkey")
          .setValueToSet(randomVarValue)
          .build()
  ).build().toByteArray()

  private val clientVarRequestValue = MessageProtos.Wrapper.newBuilder().setClientVarRequest(
      AccountProtos.ClientVarRequest.newBuilder()
          .setKey("testkey")
          .setValueToSet("")
          .build()
  ).build().toByteArray()

  private val clientInfoRequest = MessageProtos.Wrapper.newBuilder().setClientInfoRequest(
      AccountProtos.ClientInfoRequest.newBuilder().build()
  ).build().toByteArray()

  @Test
  fun `client can login to server and request essential data`() {
    socket.connect()

    // Send chat message
    socket.send(chatPayload)
    // val resp1 = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)
    // Assert.assertNotNull(resp1)
    // socket.send(chatMapMoveCommandPayload)

    // Set and request client vars
    socket.send(clientVarRequestSetValue)
    val resp2 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
    Assert.assertNotNull(resp2)
    Assert.assertEquals(randomVarValue, resp2!!.value)

    socket.send(clientVarRequestValue)
    val resp3 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
    Assert.assertNotNull(resp3)
    Assert.assertEquals(randomVarValue, resp3!!.value)

    // Send client info request
    socket.send(clientInfoRequest)
    val resp4 = socket.receive<AccountProtos.ClientInfoResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE)
    println(resp4)

    // Move player bestia and await component updates

    // Logout

    socket.close()
  }
}
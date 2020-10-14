package net.bestia.zoneserver.integration

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.ChatProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.ClientSocket
import org.awaitility.Awaitility.await
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*

@IntegrationTest
class BootableSmokeTest {

  private val chatPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText("Hello World")
          .build()
  ).build().toByteArray()

  private val pingPayload = MessageProtos.Wrapper.newBuilder().setPingRequest(
      AccountProtos.PingRequest.newBuilder()
          .setSequenceNumber(123)
          .build()
  ).build().toByteArray()

  private val chatMapMoveCommandPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText("/mm 0 20")
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
    Thread.sleep(5000)

    ClientSocket("127.0.0.1", 8990).use { socket ->
      socket.connect()
      val initialClientInfo = socket.receive<AccountProtos.ClientInfoResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE)
      Assert.assertNotNull(initialClientInfo)
      Assert.assertEquals(4, initialClientInfo!!.bestiaSlotCount)
      Assert.assertEquals(1, initialClientInfo.ownedBestiaEntityIdsList.size)

      /*
      // Send chat message
      socket.send(chatPayload)
      val resp1 = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)
      Assert.assertNotNull(resp1)
      Assert.assertEquals("Doom Master", resp1!!.senderNickname)
      Assert.assertEquals("Hello World", resp1.text)
      Assert.assertTrue(initialClientInfo.ownedBestiaEntityIdsList.contains(resp1.entityId))

      socket.send(chatServerCommandPayload)
      val resp2 = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)
      Assert.assertNotNull(resp2)
      Assert.assertTrue(resp2!!.senderNickname.isEmpty())
      Assert.assertTrue(resp2.senderNickname.isEmpty())
      Assert.assertNotEquals(0, resp2.time)
      Assert.assertTrue(resp2.text.contains("Bestia Behemoth Server: v"))

      // Set and request client vars
      socket.send(clientVarRequestSetValue)
      val resp3 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
      Assert.assertNotNull(resp3)
      Assert.assertEquals(randomVarValue, resp3!!.value)

      socket.send(clientVarRequestValue)
      val resp4 = socket.receive<AccountProtos.ClientVarResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE)
      Assert.assertNotNull(resp4)
      Assert.assertEquals(randomVarValue, resp4!!.value)

      // Send client info request
      socket.send(clientInfoRequest)
      val resp5 = socket.receive<AccountProtos.ClientInfoResponse>(MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE)*/

      // Move player Bestia and await component updates via a Script ticking damage entity.
      socket.send(chatMapMoveCommandPayload)
      val test = socket.receive<ChatProtos.ChatResponse>(MessageProtos.Wrapper.PayloadCase.CHAT_RESPONSE)

      // await().atMost(Duration.ofSeconds(30)).until { false }

      val responseTimeMs = getResponseTime(socket, 10)
      println("Avg. Behemeoth Roundtrip Time: $responseTimeMs ms")

      // Logout
      socket.printStatistics()

      Thread.sleep(30000)
    }
  }

  private fun getResponseTime(socket: ClientSocket, repeats: Int = 5): Int {
    val times = mutableListOf<Double>()
    for (i in 1..repeats) {
      val send = System.currentTimeMillis().toDouble()
      socket.send(pingPayload)
      val pong = socket.receive<AccountProtos.PingResponse>(MessageProtos.Wrapper.PayloadCase.PING_RESPONSE)
      Assert.assertNotNull(pong)
      val received = System.currentTimeMillis().toDouble()
      times.add(received - send)
    }

    return (times.sum() / times.size).toInt()
  }
}
package net.bestia.zoneserver.integration

import net.bestia.messages.proto.*
import net.bestia.zoneserver.ClientSocket
import net.bestia.zoneserver.receive
import org.junit.Assert
import org.junit.jupiter.api.Test
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

  private val chatCommandText = "/mm 10 20"
  private val chatMapMoveCommandPayload = MessageProtos.Wrapper.newBuilder().setChatRequest(
      ChatProtos.ChatRequest.newBuilder()
          .setMode(ChatProtos.ChatMode.PUBLIC)
          .setText(chatCommandText)
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

  private fun getAttackListRequest(playerBestiaId: Long): ByteArray {
    return MessageProtos.Wrapper.newBuilder().setAttackListRequest(
        AttackProtos.AttackListRequest.newBuilder().setPlayerBestiaId(playerBestiaId).build()
    ).build().toByteArray()
  }

  @Test
  fun `client can login to server and request essential data`() {
    Thread.sleep(5000)

    ClientSocket("127.0.0.1", 8990).use { socket ->
      socket.connect()
      val initialClientInfo = socket.receive<AccountProtos.ClientInfoResponse>()
      Assert.assertNotNull(initialClientInfo)
      Assert.assertEquals(4, initialClientInfo!!.bestiaSlotCount)
      Assert.assertEquals(1, initialClientInfo.ownedBestiasList.size)
      Assert.assertEquals("Master is the active entity", initialClientInfo.masterEntityId, initialClientInfo.activeEntityId)

      // Send chat message
      socket.send(chatPayload)
      val resp1 = socket.receive<ChatProtos.ChatResponse>()
      Assert.assertNotNull(resp1)
      Assert.assertEquals("Doom Master", resp1!!.senderNickname)
      Assert.assertEquals("Hello World", resp1.text)
      Assert.assertTrue(initialClientInfo.ownedBestiasList.any { it.entityId == resp1.entityId })

      socket.send(chatServerCommandPayload)
      val resp2 = socket.receive<ChatProtos.ChatResponse>()
      Assert.assertNotNull(resp2)
      Assert.assertTrue(resp2!!.senderNickname.isEmpty())
      Assert.assertTrue(resp2.senderNickname.isEmpty())
      Assert.assertNotEquals(0, resp2.time)
      Assert.assertTrue(resp2.text.contains("Bestia Behemoth Server: v"))

      // Set and request client vars
      socket.send(clientVarRequestSetValue)
      val resp3 = socket.receive<AccountProtos.ClientVarResponse>()
      Assert.assertNotNull(resp3)
      Assert.assertEquals(randomVarValue, resp3!!.value)

      socket.send(clientVarRequestValue)
      val resp4 = socket.receive<AccountProtos.ClientVarResponse>()
      Assert.assertNotNull(resp4)
      Assert.assertEquals(randomVarValue, resp4!!.value)

      // Send client info request
      socket.send(clientInfoRequest)
      val clientInfoResponse = socket.receive<AccountProtos.ClientInfoResponse>()
      Assert.assertNotNull(clientInfoResponse)
      // TODO Perform more tests on response

      socket.send(getAttackListRequest(initialClientInfo.activeEntityId))
      val attackListResponse = socket.receive<AttackProtos.AttackListResponse>()
      Assert.assertNotNull(attackListResponse)
      // TODO Test content of attack list response

      // Move player Bestia and await component updates via a Script ticking damage entity.
      socket.send(chatMapMoveCommandPayload)
      val posComp = socket.receive<ComponentProtos.PositionComponent>()
      Assert.assertNotNull(posComp)
      Assert.assertEquals(10L, posComp!!.position.x)
      Assert.assertEquals(20L, posComp.position.y)
      val commandChatResponse = socket.receive<ChatProtos.ChatResponse>()
      Assert.assertNotNull(commandChatResponse)
      Assert.assertEquals(chatCommandText, commandChatResponse!!.text)
      Assert.assertEquals(ChatProtos.ChatMode.SYSTEM, commandChatResponse.mode)

      // TODO check the component updates

      // TODO switch active bestia to another bestia
      // TODO later request map data from the server


      val responseTimeMs = getResponseTime(socket, 10)
      println("Avg. Behemeoth Roundtrip Time: $responseTimeMs ms")

      socket.printStatistics()
    }
  }

  private fun getResponseTime(socket: ClientSocket, repeats: Int = 5): Int {
    val times = mutableListOf<Double>()
    for (i in 1..repeats) {
      val send = System.currentTimeMillis().toDouble()
      socket.send(pingPayload)
      val pong = socket.receive<AccountProtos.PingResponse>()
      Assert.assertNotNull(pong)
      val received = System.currentTimeMillis().toDouble()
      times.add(received - send)
    }

    return (times.sum() / times.size).toInt()
  }
}
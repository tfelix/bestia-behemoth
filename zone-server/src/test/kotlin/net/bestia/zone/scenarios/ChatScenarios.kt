package net.bestia.zone.scenarios

import net.bestia.zone.system.ChatCMSG
import net.bestia.zone.system.ChatSMSG
import net.bestia.zone.system.PingCMSG
import net.bestia.zone.system.PongSMSG
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatScenarios : BestiaNoSocketScenario() {

  @Test
  fun `send public chat sends message to all nearby players`() {
    // todo setup client with additional player position around the spawn.
    clientPlayer1.sendMessage(PingCMSG(clientPlayer1.connectedPlayerId))

    val pong = clientPlayer1.tryGetLastReceived(PongSMSG::class)

    assertNotNull(pong)
  }

  @Test
  fun `send whisper chat to connected player delivers message`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.WHISPER,
        text = "helloworld",
        targetUsername = "player2"
      )
    )

    val whisperChatRx = clientPlayer2.getLastReceived(ChatSMSG::class)

    assertEquals("helloworld", whisperChatRx.text)
    assertEquals("player1", whisperChatRx.senderUsername)
    assertEquals(ChatCMSG.Type.WHISPER, whisperChatRx.type)
  }

  @Test
  fun `send whisper chat to not connected player echos with error`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.WHISPER,
        text = "helloworld",
        targetUsername = "playerUnknown"
      )
    )

    val whisperChatRx = clientPlayer2.tryGetLastReceived(ChatSMSG::class)
    assertNull(whisperChatRx)

    val whisperChatErrorRx = clientPlayer1.getLastReceived(ChatSMSG::class)

    assertEquals("error.player_not_found", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send party chat delivers message to online party players`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.PARTY,
        text = "helloworld",
      )
    )

    val whisperChatErrorRx = clientPlayer1.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send party chat when not in a party echos with error`() {
    clientPlayer3.sendMessage(
      ChatCMSG(
        playerId = clientPlayer3.connectedPlayerId,
        type = ChatCMSG.Type.PARTY,
        text = "helloworld",
      )
    )

    val whisperChatErrorRx = clientPlayer3.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send guild chat delivers message to online guild players`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.GUILD,
        text = "helloworld",
      )
    )

    val guildChatErrorRx = clientPlayer1.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", guildChatErrorRx.text)
    assertNull(guildChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, guildChatErrorRx.type)
  }

  @Test
  fun `send guild chat when not in a guild echos with error`() {
    clientPlayer3.sendMessage(
      ChatCMSG(
        playerId = clientPlayer3.connectedPlayerId,
        type = ChatCMSG.Type.GUILD,
        text = "helloworld",
      )
    )

    val whisperChatErrorRx = clientPlayer3.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send non-privileged chat command when not connected as privileged player executes`() {
    clientPlayer3.sendMessage(
      ChatCMSG(
        playerId = clientPlayer3.connectedPlayerId,
        type = ChatCMSG.Type.COMMAND,
        text = "/online",
      )
    )

    val whisperChatErrorRx = clientPlayer3.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send privileged chat command when not connected as privileged player echos error`() {
    clientPlayer3.sendMessage(
      ChatCMSG(
        playerId = clientPlayer3.connectedPlayerId,
        type = ChatCMSG.Type.COMMAND,
        text = "/mm 10 10",
      )
    )

    val whisperChatErrorRx = clientPlayer3.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }

  @Test
  fun `send privileged chat command when connected as privileged player executes command`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.COMMAND,
        text = "/mm 10 10",
      )
    )

    val whisperChatErrorRx = clientPlayer1.getLastReceived(ChatSMSG::class)

    assertEquals("error.not_supported", whisperChatErrorRx.text)
    assertNull(whisperChatErrorRx.senderUsername)
    assertEquals(ChatCMSG.Type.ERROR, whisperChatErrorRx.type)
  }
}



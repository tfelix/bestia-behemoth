package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.chat.ChatCMSG
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.socket.PingCMSG
import net.bestia.zone.socket.PongSMSG
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

  /**
   * Used to send `/mm 10 10` and expect `error.not_supported`, which held only because nothing implemented
   * `/mm`. It was named as a privilege test but never was: `GameClientMockFactory` gives every mock client
   * the full authority set, so player1 and player3 are equally privileged and both were simply failing to
   * match any command. Implementing `/mm` turned that into a failure and exposed it.
   *
   * Renamed to say what it checks, and pointed at a string nothing will ever claim. Real coverage of the
   * authority gate is [net.bestia.zone.chat.ChatCommandHandlerTest], which can build the restricted
   * authority set the mock factory does not expose.
   */
  @Test
  fun `an unrecognised chat command echos an error`() {
    clientPlayer3.sendMessage(
      ChatCMSG(
        playerId = clientPlayer3.connectedPlayerId,
        type = ChatCMSG.Type.COMMAND,
        text = "/nosuchcommand 10 10",
      )
    )

    assertNull(clientPlayer3.tryGetLastReceived(ChatSMSG::class))
    assertEquals(
      OperationErrorSMSG(OpError.CHAT_COMMAND_UNKNOWN),
      clientPlayer3.getLastReceived(OperationErrorSMSG::class)
    )
  }

  /**
   * The other half of the same contract, and the part no test covered: a command that *is* recognised must be
   * consumed silently. Only the chat layer is under test here - `/mm` queues the move and answers nothing, so
   * this passes or fails on whether the command was matched at all, not on whether the player ends up anywhere.
   */
  @Test
  fun `a recognised chat command is consumed without an error reply`() {
    clientPlayer1.sendMessage(
      ChatCMSG(
        playerId = clientPlayer1.connectedPlayerId,
        type = ChatCMSG.Type.COMMAND,
        text = "/mm 10 10",
      )
    )

    assertNull(clientPlayer1.tryGetLastReceived(ChatSMSG::class))
    assertNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }
}



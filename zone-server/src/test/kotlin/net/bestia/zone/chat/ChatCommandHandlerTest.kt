package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the player is told when a command does not run. The three reasons used to arrive as one string, and a
 * GM command a player simply may not use read as the feature being missing.
 */
class ChatCommandHandlerTest {

  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val connectionInfo = mockk<ConnectionInfoService>()

  private fun handle(cmdText: String, authorities: Set<Authority>, vararg commands: ChatCommand) {
    every { connectionInfo.getAuthorities(PLAYER) } returns authorities

    ChatCommandHandler(commands.toList(), connectionInfo, out).handleChatCommand(PLAYER, cmdText)
  }

  private fun answer(): SMSG {
    val sent = slot<SMSG>()
    verify { out.sendToPlayer(PLAYER, capture(sent)) }

    return sent.captured
  }

  @Test
  fun `a command the player may use runs and answers nothing`() {
    val exp = FakeChatCommand("/exp", requiredAuthority = Authority.EXP)

    handle("/exp 100", setOf(Authority.EXP), exp)

    assertTrue(exp.executed)
    verify(exactly = 0) { out.sendToPlayer(PLAYER, any<SMSG>()) }
  }

  @Test
  fun `a command the player may not use is refused as a permission problem`() {
    val exp = FakeChatCommand("/exp", requiredAuthority = Authority.EXP)

    handle("/exp 100", setOf(Authority.MAP_MOVE), exp)

    assertFalse(exp.executed)
    assertEquals(OperationErrorSMSG(OpError.CHAT_COMMAND_NO_PERMISSION), answer())
  }

  /**
   * The distinction is the whole point: a command nothing claims has to stay "no such command", or the new
   * wording would tell every typo it was a matter of privilege.
   */
  @Test
  fun `a command nothing matches is refused as unknown`() {
    val exp = FakeChatCommand("/exp", requiredAuthority = Authority.EXP)

    handle("/nosuchcommand", setOf(Authority.MAP_MOVE), exp)

    assertEquals(OperationErrorSMSG(OpError.CHAT_COMMAND_UNKNOWN), answer())
  }

  /**
   * A gated command is only a permission problem for the player it is gated against - the same text typed by
   * a holder that no command actually claims is still unknown.
   */
  @Test
  fun `an ungated command that does not match stays unknown`() {
    val leave = FakeChatCommand("/leave")

    handle("/nosuchcommand", emptySet(), leave)

    assertEquals(OperationErrorSMSG(OpError.CHAT_COMMAND_UNKNOWN), answer())
  }

  @Test
  fun `a command that throws is reported as a failure, not as a missing command`() {
    val broken = FakeChatCommand("/spawn", requiredAuthority = Authority.SPAWN, throws = true)

    handle("/spawn wolf", setOf(Authority.SPAWN), broken)

    assertEquals(OperationErrorSMSG(OpError.CHAT_COMMAND_FAILED), answer())
  }

  @Test
  fun `help lists only the commands the player may use`() {
    handle(
      "/help",
      setOf(Authority.MAP_MOVE),
      FakeChatCommand("/exp", requiredAuthority = Authority.EXP),
      FakeChatCommand("/mm", requiredAuthority = Authority.MAP_MOVE)
    )

    assertEquals(ChatSMSG(text = "/mm - fake", type = ChatCMSG.Type.COMMAND), answer())
  }

  private class FakeChatCommand(
    private val prefix: String,
    override val requiredAuthority: Authority? = null,
    private val throws: Boolean = false
  ) : ChatCommand() {

    var executed = false
      private set

    override fun getHelpText(): String {
      return "$prefix - fake"
    }

    override fun isMatch(cmdText: String): Boolean {
      return cmdText.trim().startsWith(prefix)
    }

    override fun execute(playerId: Long, cmdText: String): Boolean {
      if (throws) {
        throw IllegalStateException("broken command")
      }

      executed = true

      return true
    }
  }

  private companion object {
    private const val PLAYER = 42L
  }
}

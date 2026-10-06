package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.junit.jupiter.api.Test

/**
 * A whisper that goes nowhere has to say so. It is the one chat line nothing will ever send again, so a
 * silent drop leaves the sender believing they were heard.
 */
class ChatHandlerTest {

  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val masters = mockk<MasterResolver>()
  private val skillGate = mockk<BasicSkillGate>()

  private val handler = ChatHandler(
    outMessageProcessor = out,
    masterOperations = masters,
    connectionInfoService = mockk(relaxed = true),
    world = mockk<WorldView>(relaxed = true),
    chatCommandHandler = mockk(relaxed = true),
    basicSkillGate = skillGate
  )

  private fun whisper(target: String, text: String = "helloworld") {
    every { skillGate.mayChat(SENDER) } returns true
    every { masters.getSelectedMasterByAccountId(SENDER) } returns mockk<Master> {
      every { name } returns "sender"
    }

    handler.handle(
      ChatCMSG(
        playerId = SENDER,
        type = ChatCMSG.Type.WHISPER,
        text = text,
        targetUsername = target
      )
    )
  }

  private fun unavailable(target: String) =
    OperationErrorSMSG(OpError.CHAT_WHISPER_TARGET_UNAVAILABLE, listOf(target))

  @Test
  fun `a whisper to a connected master is delivered`() {
    every { masters.getAccountIdByMasterName("target") } returns TARGET
    every { out.isPlayerConnected(TARGET) } returns true

    whisper("target")

    verify {
      out.sendToPlayer(
        TARGET,
        ChatSMSG(type = ChatCMSG.Type.WHISPER, text = "helloworld", senderUsername = "sender")
      )
    }
  }

  @Test
  fun `a whisper to a name nobody has is refused`() {
    every { masters.getAccountIdByMasterName("nobody") } throws MasterNotFoundException()

    whisper("nobody")

    verify { out.sendToPlayer(SENDER, unavailable("nobody")) }
  }

  /**
   * The same answer as the unknown name above, deliberately: a whisper that could tell the two apart would
   * be a way to ask who is online.
   */
  @Test
  fun `a whisper to a master who is not connected is refused the same way`() {
    every { masters.getAccountIdByMasterName("target") } returns TARGET
    every { out.isPlayerConnected(TARGET) } returns false

    whisper("target")

    verify { out.sendToPlayer(SENDER, unavailable("target")) }
    verify(exactly = 0) { out.sendToPlayer(TARGET, any<ChatSMSG>()) }
  }

  /** Relayed to every client in range, so the length a client may send is the length every reader is shown. */
  @Test
  fun `an over-long line is not relayed`() {
    every { masters.getAccountIdByMasterName("target") } returns TARGET
    every { out.isPlayerConnected(TARGET) } returns true

    whisper("target", text = "x".repeat(201))

    verify(exactly = 0) { out.sendToPlayer(TARGET, any<ChatSMSG>()) }
  }

  /** A bell or a right-to-left override in someone else's chat window is never something the reader asked for. */
  @Test
  fun `control characters are stripped before relaying`() {
    every { masters.getAccountIdByMasterName("target") } returns TARGET
    every { out.isPlayerConnected(TARGET) } returns true

    whisper("target", text = "hello\u0007\u202Eworld")

    verify {
      out.sendToPlayer(TARGET, ChatSMSG(type = ChatCMSG.Type.WHISPER, text = "helloworld", senderUsername = "sender"))
    }
  }

  companion object {
    private const val SENDER = 1L
    private const val TARGET = 2L
  }
}

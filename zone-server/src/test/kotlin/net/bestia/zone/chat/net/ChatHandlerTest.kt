package net.bestia.zone.chat.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.MasterNotFoundException
import net.bestia.zone.account.MasterResolver
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.junit.jupiter.api.Test
import net.bestia.zone.chat.ChatSMSG
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.party.PartyAudience
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.identity.GroupMember

/**
 * A whisper that goes nowhere has to say so. It is the one chat line nothing will ever send again, so a
 * silent drop leaves the sender believing they were heard.
 */
class ChatHandlerTest {

  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val masters = mockk<MasterResolver>()
  private val skillGate = mockk<BasicSkillGate>()
  private val partyAudience = mockk<PartyAudience>()
  private val guildAudience = mockk<GuildAudience>()
  private val groupChat = mockk<GroupChat>(relaxed = true)

  private val handler = ChatHandler(
    outMessageProcessor = out,
    masterOperations = masters,
    connectionInfoService = mockk(relaxed = true),
    world = mockk<WorldView>(relaxed = true),
    chatCommandHandler = mockk(relaxed = true),
    basicSkillGate = skillGate,
    partyAudience = partyAudience,
    guildAudience = guildAudience,
    groupChat = groupChat,
  )

  private fun whisper(target: String, text: String = "helloworld") {
    every { skillGate.mayChat(SENDER) } returns true
    every { masters.getSelectedMasterByAccountId(SENDER) } returns mockk<Master> {
      every { name } returns "sender"
    }

    handler.handle(
      ChatCMSG(
        playerId = SENDER,
        type = ChatType.WHISPER,
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
        ChatSMSG(type = ChatType.WHISPER, text = "helloworld", senderUsername = "sender")
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
      out.sendToPlayer(TARGET, ChatSMSG(type = ChatType.WHISPER, text = "helloworld", senderUsername = "sender"))
    }
  }

  private fun groupLine(type: ChatType, text: String = "together") {
    every { skillGate.mayChat(SENDER) } returns true
    every { masters.getSelectedMasterByAccountId(SENDER) } returns mockk<Master> {
      every { name } returns "sender"
    }

    handler.handle(ChatCMSG(playerId = SENDER, type = type, text = text, targetUsername = null))
  }

  @Test
  fun `a party line goes to the whole party`() {
    every { partyAudience.of(SENDER) } returns MEMBERS

    groupLine(ChatType.PARTY)

    verify { groupChat.deliver(ChatType.PARTY, "sender", "together", MEMBERS) }
  }

  @Test
  fun `a party line from outside a party is refused`() {
    every { partyAudience.of(SENDER) } returns null

    groupLine(ChatType.PARTY)

    verify { out.sendToPlayer(SENDER, OperationErrorSMSG(OpError.PARTY_NOT_IN_PARTY)) }
    verify(exactly = 0) { groupChat.deliver(any(), any(), any(), any()) }
  }

  @Test
  fun `a guild line goes to the whole guild`() {
    every { guildAudience.of(SENDER) } returns MEMBERS

    groupLine(ChatType.GUILD)

    verify { groupChat.deliver(ChatType.GUILD, "sender", "together", MEMBERS) }
  }

  @Test
  fun `a guild line from outside a guild is refused`() {
    every { guildAudience.of(SENDER) } returns null

    groupLine(ChatType.GUILD)

    verify { out.sendToPlayer(SENDER, OperationErrorSMSG(OpError.GUILD_NOT_A_MEMBER)) }
  }

  companion object {
    private const val SENDER = 1L
    private const val TARGET = 2L
    private val MEMBERS = listOf(GroupMember(SENDER, 10), GroupMember(TARGET, 20))
  }
}

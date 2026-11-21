package net.bestia.zone.chat.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.MasterResolver
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.junit.jupiter.api.Test
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.party.PartyAudience
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.chat.WhisperChat
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
  private val whisperChat = mockk<WhisperChat>()

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
    whisperChat = whisperChat,
    scryVoice = mockk(relaxed = true),
  )

  private val sender = mockk<Master> { every { name } returns "sender" }

  private fun whisper(target: String, text: String = "helloworld") {
    every { skillGate.mayChat(SENDER) } returns true
    every { masters.getSelectedMasterByAccountId(SENDER) } returns sender

    handler.handle(
      ChatCMSG(
        playerId = SENDER,
        type = ChatType.WHISPER,
        text = text,
        targetUsername = target
      )
    )
  }

  private fun unavailable(target: String): OperationErrorSMSG {
    return OperationErrorSMSG(OpError.CHAT_WHISPER_TARGET_UNAVAILABLE, listOf(target))
  }

  @Test
  fun `a whisper that arrived is not answered with a refusal`() {
    every { whisperChat.deliver(sender, "target", "helloworld") } returns true

    whisper("target")

    verify(exactly = 0) { out.sendToPlayer(SENDER, any<OperationErrorSMSG>()) }
  }

  @Test
  fun `a whisper that had nowhere to go is refused`() {
    every { whisperChat.deliver(sender, "nobody", "helloworld") } returns false

    whisper("nobody")

    verify { out.sendToPlayer(SENDER, unavailable("nobody")) }
  }

  /** Relayed to every client in range, so the length a client may send is the length every reader is shown. */
  @Test
  fun `an over-long line is not relayed`() {
    whisper("target", text = "x".repeat(201))

    verify(exactly = 0) { whisperChat.deliver(any(), any(), any()) }
  }

  /** A bell or a right-to-left override in someone else's chat window is never something the reader asked for. */
  @Test
  fun `control characters are stripped before relaying`() {
    every { whisperChat.deliver(any(), any(), any()) } returns true

    whisper("target", text = "hello\u0007\u202Eworld")

    verify { whisperChat.deliver(sender, "target", "helloworld") }
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

package net.bestia.zone.companion.query

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.LearnedSkill
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.chat.ChatType
import net.bestia.zone.chat.GroupChat
import net.bestia.zone.identity.GroupMember
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildAudience
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.party.PartyAudience
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.skill.tree.MasterSkillTreeNode
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.AccountId
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

/** Party and guild chat from the app keeps every rule the game's own chat has. */
class ChatCommandTest {

  private val owned = mockk<OwnedMasters>()
  private val skillGate = mockk<BasicSkillGate>()
  private val partyAudience = mockk<PartyAudience>()
  private val guildAudience = mockk<GuildAudience>()
  private val groupChat = mockk<GroupChat>(relaxed = true)

  /** Runs IO work at once, as if the account's inbox had nothing else queued. */
  private val inbox = object : AccountTaskExecutor {
    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      error("app chat runs on the IO lane")
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> =
      CompletableFuture.completedFuture(task())
  }

  private val sessions = mockk<ConnectionInfoService>()
  private val learnedSkills = mockk<LearnedSkillRepository>()
  private val skillTree = MasterSkillTreeRegistry().apply {
    load(listOf(MasterSkillTreeNode(BASIC_SKILL, "BASIC_SKILL", maxLevel = 5, tree = MasterSkillTreeRegistry.NOVICE_TREE)))
  }

  private val command =
    ChatCommand(owned, skillGate, partyAudience, guildAudience, groupChat, inbox, sessions, skillTree, learnedSkills)

  private val master = mockk<Master> { every { name } returns "Ayla" }

  private fun send(channel: String = "PARTY", text: String = "on my way") =
    command.send("c", ACCOUNT, ZoneQuery.SendChat(MASTER, channel, text))

  private fun givenMaySpeak() {
    every { owned.find(ACCOUNT, MASTER) } returns master
    givenStoredRank(2)
  }

  private fun givenStoredRank(rank: Int) {
    every { sessions.isPlaying(ACCOUNT, MASTER) } returns false
    every { learnedSkills.findByMasterIdAndSkillId(MASTER, BASIC_SKILL) } returns mockk<LearnedSkill> {
      every { level } returns rank
    }
  }

  @Test
  fun `a party line from the app goes to the whole party`() {
    givenMaySpeak()
    every { partyAudience.ofMaster(MASTER) } returns MEMBERS

    val reply = send()

    assertEquals(ZoneAnswer.ChatSent(null), reply.answer)
    verify { groupChat.deliver(ChatType.PARTY, "Ayla", "on my way", MEMBERS) }
  }

  @Test
  fun `a guild line from the app goes to the whole guild`() {
    givenMaySpeak()
    every { guildAudience.ofMaster(MASTER) } returns MEMBERS

    send(channel = "GUILD")

    verify { groupChat.deliver(ChatType.GUILD, "Ayla", "on my way", MEMBERS) }
  }

  @Test
  fun `someone else's master is not found`() {
    every { owned.find(ACCOUNT, MASTER) } returns null

    assertEquals(Status.NOT_FOUND, send().status)
    verify(exactly = 0) { groupChat.deliver(any(), any(), any(), any()) }
  }

  @Test
  fun `the skill gate holds on a phone too`() {
    every { owned.find(ACCOUNT, MASTER) } returns master
    givenStoredRank(1)

    assertEquals(ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.CHAT_LOCKED), send().answer)
    verify(exactly = 0) { groupChat.deliver(any(), any(), any(), any()) }
  }

  /** A rank bought this session is not stored until the master is saved, so while it is played the live one counts. */
  @Test
  fun `a master being played is judged by its live rank`() {
    every { owned.find(ACCOUNT, MASTER) } returns master
    every { sessions.isPlaying(ACCOUNT, MASTER) } returns true
    every { skillGate.rankOf(ACCOUNT) } returns 2
    every { partyAudience.ofMaster(MASTER) } returns MEMBERS

    assertEquals(ZoneAnswer.ChatSent(null), send().answer)
  }

  @Test
  fun `a master outside a party or guild is told so`() {
    givenMaySpeak()
    every { partyAudience.ofMaster(MASTER) } returns null
    every { guildAudience.ofMaster(MASTER) } returns null

    assertEquals(ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_PARTY), send(channel = "PARTY").answer)
    assertEquals(ZoneAnswer.ChatSent(ZoneAnswer.ChatSent.NOT_IN_GUILD), send(channel = "GUILD").answer)
  }

  /** Local say needs a body in the world, and the app has none to speak with. */
  @Test
  fun `only party and guild can be said from the app`() {
    assertEquals(Status.INVALID, send(channel = "PUBLIC").status)
  }

  @Test
  fun `an empty or over-long line is refused`() {
    assertEquals(Status.INVALID, send(text = "\u0007 ").status)
    assertEquals(Status.INVALID, send(text = "x".repeat(201)).status)
  }

  @Test
  fun `control characters are stripped before the line goes out`() {
    givenMaySpeak()
    every { partyAudience.ofMaster(MASTER) } returns MEMBERS

    send(text = "on my\u202E way")

    verify { groupChat.deliver(ChatType.PARTY, "Ayla", "on my way", MEMBERS) }
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER = 10L
    const val BASIC_SKILL = 3L
    val MEMBERS = listOf(GroupMember(1, 10), GroupMember(2, 20))
  }
}

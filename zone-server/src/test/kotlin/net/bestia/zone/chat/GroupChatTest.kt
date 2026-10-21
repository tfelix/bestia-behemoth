package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.identity.GroupMember
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The game and the app are one conversation: a line reaches the game where it is played, and the app everywhere. */
class GroupChatTest {

  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val sessions = mockk<ConnectionInfoService>()
  private val published = mutableListOf<ZoneEvent>()

  private val groupChat = GroupChat(out, sessions, EventPublisher { published += it; true })

  @Test
  fun `the game hears a line only where a member is being played`() {
    every { sessions.isPlaying(SENDER, SENDER_MASTER) } returns true
    every { sessions.isPlaying(AWAY, AWAY_MASTER) } returns false

    groupChat.deliver(ChatType.PARTY, "Ayla", "together", MEMBERS)

    val line = ChatSMSG(type = ChatType.PARTY, text = "together", senderUsername = "Ayla")
    verify { out.sendToPlayer(SENDER, line) }
    verify(exactly = 0) { out.sendToPlayer(AWAY, any<ChatSMSG>()) }
  }

  @Test
  fun `every member gets the line in the app, played or not`() {
    every { sessions.isPlaying(any(), any()) } returns false

    groupChat.deliver(ChatType.GUILD, "Ayla", "together", MEMBERS)

    val lines = published.filterIsInstance<ZoneEvent.ChatLine>()
    assertEquals(listOf(SENDER to SENDER_MASTER, AWAY to AWAY_MASTER), lines.map { it.accountId to it.masterId })
    lines.forEach {
      assertEquals("GUILD", it.channel)
      assertEquals("Ayla", it.senderName)
      assertEquals("together", it.text)
    }
  }

  private companion object {
    const val SENDER = 1L
    const val SENDER_MASTER = 10L
    const val AWAY = 2L
    const val AWAY_MASTER = 20L
    val MEMBERS = listOf(GroupMember(SENDER, SENDER_MASTER), GroupMember(AWAY, AWAY_MASTER))
  }
}

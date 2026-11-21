package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bus.AppPresence
import net.bestia.bus.EventPublisher
import net.bestia.bus.contract.ZoneEvent
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhisperChatTest {

  private val masters = mockk<MasterRepository>()
  private val sessions = mockk<ConnectionInfoService>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val apps = mockk<AppPresence>()
  private val published = mutableListOf<ZoneEvent>()

  private val whisperChat = WhisperChat(masters, sessions, out, apps, EventPublisher { published += it; true })

  private val writer = master(WRITER, WRITER_MASTER, "Ayla")
  private val target = master(TARGET, TARGET_MASTER, "Bren")

  private fun master(accountId: Long, masterId: Long, masterName: String): Master {
    return mockk {
      every { id } returns masterId
      every { name } returns masterName
      every { account.id } returns accountId
    }
  }

  @BeforeEach
  fun bothInTheGame() {
    every { masters.findByName("Bren") } returns target
    every { sessions.isPlaying(WRITER, WRITER_MASTER) } returns true
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns true
    every { apps.isOpen(any()) } returns false
  }

  @Test
  fun `a whisper reaches the master it names`() {
    assertTrue(whisperChat.deliver(writer, "Bren", "psst"))

    verify { out.sendToPlayer(TARGET, ChatSMSG(type = ChatType.WHISPER, text = "psst", senderUsername = "Ayla")) }
  }

  /** The writer sees the line only once it arrived, under the name as it is stored. */
  @Test
  fun `the writer gets a copy that names the target`() {
    every { masters.findByName("bren") } returns target

    whisperChat.deliver(writer, "bren", "psst")

    val ownCopy = ChatSMSG(type = ChatType.WHISPER, text = "psst", senderUsername = "Ayla", targetUsername = "Bren")
    verify { out.sendToPlayer(WRITER, ownCopy) }
  }

  @Test
  fun `both apps get the line, the writer's naming the target`() {
    whisperChat.deliver(writer, "Bren", "psst")

    val lines = published.filterIsInstance<ZoneEvent.ChatLine>()
    assertEquals(listOf(TARGET to TARGET_MASTER, WRITER to WRITER_MASTER), lines.map { it.accountId to it.masterId })
    assertEquals(listOf(null, "Bren"), lines.map { it.targetName })
    lines.forEach {
      assertEquals("WHISPER", it.channel)
      assertEquals("Ayla", it.senderName)
    }
  }

  @Test
  fun `a player with the app open is reached outside the game`() {
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns false
    every { apps.isOpen(TARGET) } returns true

    assertTrue(whisperChat.deliver(writer, "Bren", "psst"))

    verify(exactly = 0) { out.sendToPlayer(TARGET, any<ChatSMSG>()) }
    assertEquals(2, published.size)
  }

  /** Written in the app while not playing: there is no game window to show the copy in. */
  @Test
  fun `a writer outside the game gets the copy in the app only`() {
    every { sessions.isPlaying(WRITER, WRITER_MASTER) } returns false

    whisperChat.deliver(writer, "Bren", "psst")

    verify(exactly = 0) { out.sendToPlayer(WRITER, any<ChatSMSG>()) }
  }

  @Test
  fun `a whisper to a name nobody has goes nowhere`() {
    every { masters.findByName("Nobody") } returns null

    assertFalse(whisperChat.deliver(writer, "Nobody", "psst"))

    verify(exactly = 0) { out.sendToPlayer(any(), any<ChatSMSG>()) }
    assertTrue(published.isEmpty())
  }

  /** An account that plays another of its masters, with no app open, must not read what was meant for this one. */
  @Test
  fun `a whisper to a player neither in the game nor in an app goes nowhere`() {
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns false

    assertFalse(whisperChat.deliver(writer, "Bren", "psst"))

    verify(exactly = 0) { out.sendToPlayer(any(), any<ChatSMSG>()) }
    assertTrue(published.isEmpty())
  }

  private companion object {
    const val WRITER = 1L
    const val WRITER_MASTER = 10L
    const val TARGET = 2L
    const val TARGET_MASTER = 20L
  }
}

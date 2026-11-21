package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhisperChatTest {

  private val masters = mockk<MasterRepository>()
  private val sessions = mockk<ConnectionInfoService>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val whisperChat = WhisperChat(masters, sessions, out)

  private val writer = master(WRITER, WRITER_MASTER, "Ayla")
  private val target = master(TARGET, TARGET_MASTER, "Bren")

  private fun master(accountId: Long, masterId: Long, masterName: String): Master {
    return mockk {
      every { id } returns masterId
      every { name } returns masterName
      every { account.id } returns accountId
    }
  }

  @Test
  fun `a whisper reaches the master it names`() {
    every { masters.findByName("Bren") } returns target
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns true

    assertTrue(whisperChat.deliver(writer, "Bren", "psst"))

    verify { out.sendToPlayer(TARGET, ChatSMSG(type = ChatType.WHISPER, text = "psst", senderUsername = "Ayla")) }
  }

  /** The writer sees the line only once it arrived, under the name as it is stored. */
  @Test
  fun `the writer gets a copy that names the target`() {
    every { masters.findByName("bren") } returns target
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns true

    whisperChat.deliver(writer, "bren", "psst")

    val ownCopy = ChatSMSG(type = ChatType.WHISPER, text = "psst", senderUsername = "Ayla", targetUsername = "Bren")
    verify { out.sendToPlayer(WRITER, ownCopy) }
  }

  @Test
  fun `a whisper to a name nobody has goes nowhere`() {
    every { masters.findByName("Nobody") } returns null

    assertFalse(whisperChat.deliver(writer, "Nobody", "psst"))

    verify(exactly = 0) { out.sendToPlayer(any(), any<ChatSMSG>()) }
  }

  /** An account that plays another of its masters must not read what was meant for this one. */
  @Test
  fun `a whisper to a master nobody is playing goes nowhere`() {
    every { masters.findByName("Bren") } returns target
    every { sessions.isPlaying(TARGET, TARGET_MASTER) } returns false

    assertFalse(whisperChat.deliver(writer, "Bren", "psst"))

    verify(exactly = 0) { out.sendToPlayer(any(), any<ChatSMSG>()) }
  }

  private companion object {
    const val WRITER = 1L
    const val WRITER_MASTER = 10L
    const val TARGET = 2L
    const val TARGET_MASTER = 20L
  }
}

package net.bestia.zone.chat.net

import io.mockk.mockk
import io.mockk.verify
import net.bestia.account.Role
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class AnnounceChatCommandTest {

  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val sut = AnnounceChatCommand(out)

  @Test
  fun `a GM's text goes to everyone connected`() {
    sut.tryExecute(ACCOUNT, "/announce Server restart in 10 minutes", Role.GM.authorities)

    verify { out.sendToAllConnected(AnnouncementSMSG(Announcement.GM, listOf("Server restart in 10 minutes"))) }
  }

  @Test
  fun `a player without the authority announces nothing`() {
    assertFalse(sut.tryExecute(ACCOUNT, "/announce Free gold at the gate", Role.USER.authorities))

    verify(exactly = 0) { out.sendToAllConnected(any()) }
  }

  @Test
  fun `a bare command announces nothing`() {
    sut.tryExecute(ACCOUNT, "/announce", Role.GM.authorities)

    verify(exactly = 0) { out.sendToAllConnected(any()) }
  }

  private companion object {
    const val ACCOUNT = 7L
  }
}

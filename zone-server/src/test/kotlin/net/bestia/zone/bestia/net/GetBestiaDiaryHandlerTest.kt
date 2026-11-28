package net.bestia.zone.bestia.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.bestia.diary.BestiaDiary
import net.bestia.zone.bestia.diary.BestiaDiarySMSG
import net.bestia.zone.bestia.diary.BestiaHappened.Kind
import net.bestia.zone.bestia.persistence.DiaryLine
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/** The game's Bestias tab gets the diary as the master reads it: what happened, not how the time was spent. */
class GetBestiaDiaryHandlerTest {

  private val sessions = mockk<ConnectionInfoService>()
  private val diary = mockk<BestiaDiary>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val handler = GetBestiaDiaryHandler(sessions, diary, out)

  @Test
  fun `the diary goes to the player without its state spans`() {
    every { sessions.activeMasterIdOf(ACCOUNT) } returns MASTER
    every { diary.of(MASTER) } returns listOf(
      line(Kind.HAULED, items = "log:12"),
      line(Kind.STATE, reason = "WORKING"),
      line(Kind.ATTACKED),
    )

    handler.handle(GetBestiaDiaryCMSG(ACCOUNT))

    val sent = slot<SMSG>()
    verify { out.sendToPlayer(ACCOUNT, capture(sent)) }
    val lines = (sent.captured as BestiaDiarySMSG).lines
    assertEquals(listOf(Kind.HAULED, Kind.ATTACKED), lines.map { it.kind })
    assertEquals(mapOf("log" to 12), lines.first().items)
  }

  @Test
  fun `nothing is sent without a master in play`() {
    every { sessions.activeMasterIdOf(ACCOUNT) } returns null

    handler.handle(GetBestiaDiaryCMSG(ACCOUNT))

    verify(exactly = 0) { out.sendToPlayer(any(), any<SMSG>()) }
  }

  private fun line(kind: Kind, items: String? = null, reason: String? = null): DiaryLine {
    return DiaryLine(MASTER, BESTIA, Instant.EPOCH, kind, items = items, reason = reason)
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER = 2L
    const val BESTIA = 3L
  }
}

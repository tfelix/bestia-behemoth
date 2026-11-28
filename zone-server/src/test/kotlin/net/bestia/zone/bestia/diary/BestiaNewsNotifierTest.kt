package net.bestia.zone.bestia.diary

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.ecs.damage.OwnedBestiaDiedEvent
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BestiaNewsNotifierTest {

  private val now = Instant.parse("2026-11-28T12:00:00Z")
  private val sent = mutableListOf<Pair<Long, SMSG>>()
  private val connections = mockk<ConnectionInfoService> {
    every { activeMasters() } returns mapOf(ACCOUNT to MASTER)
    every { isPlaying(any(), any()) } answers { firstArg<Long>() == ACCOUNT && secondArg<Long>() == MASTER }
  }
  private val out = mockk<OutMessageProcessor> {
    every { sendToPlayer(any(), any<SMSG>()) } answers { sent += firstArg<Long>() to secondArg<SMSG>() }
  }
  private val notifier = BestiaNewsNotifier(connections, out, Clock.fixed(now, ZoneOffset.UTC))

  @Test
  fun `a haul reaches its playing owner with its goods`() {
    notifier.onHappened(BestiaHappened(this, MASTER, 1L, BestiaHappened.Kind.HAULED, items = mapOf("log" to 12), place = "Camp"))

    assertEquals(
      listOf(ACCOUNT to BestiaNewsSMSG(1L, BestiaHappened.Kind.HAULED, now.toEpochMilli(), mapOf("log" to 12), "Camp")),
      sent,
    )
  }

  @Test
  fun `state lines and bestias of masters not in the world tell nothing`() {
    notifier.onHappened(BestiaHappened(this, MASTER, 1L, BestiaHappened.Kind.STATE, reason = "WORKING"))
    notifier.onHappened(BestiaHappened(this, OTHER_MASTER, 2L, BestiaHappened.Kind.ATTACKED))

    assertEquals(emptyList<Pair<Long, SMSG>>(), sent)
  }

  @Test
  fun `a death is told as such`() {
    notifier.onDied(OwnedBestiaDiedEvent(this, ACCOUNT, MASTER, 1L, lostForGood = true))

    assertEquals(listOf(ACCOUNT to BestiaNewsSMSG(1L, BestiaHappened.Kind.LOST, now.toEpochMilli())), sent)
  }

  private companion object {
    const val ACCOUNT = 3L
    const val MASTER = 7L
    const val OTHER_MASTER = 8L
  }
}

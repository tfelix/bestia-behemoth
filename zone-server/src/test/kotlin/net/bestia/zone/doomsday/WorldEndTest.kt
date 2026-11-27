package net.bestia.zone.doomsday

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.doomsday.persistence.StoredWorldEnd
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.doomsday.persistence.StoredWorldEndRepository
import net.bestia.zone.doomsday.persistence.WorldLegacy
import net.bestia.zone.doomsday.persistence.WorldLegacyRepository
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.AccountConnectedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.PersistedWorld
import net.bestia.zone.world.persistence.WorldRepository
import net.bestia.zone.world.settlement.SettlementFates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional

class WorldEndTest {

  private class MovingClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId {
      return ZoneOffset.UTC
    }

    override fun withZone(zone: ZoneId?): Clock {
      return this
    }

    override fun instant(): Instant {
      return now
    }
  }

  private val clock = MovingClock(BORN.plus(Duration.ofDays(300)))
  private val world = PersistedWorld(
    name = "Genesis",
    seed = 7L,
    widthCells = 64,
    heightCells = 64,
    cellSizeMetres = 1_000.0,
    chunkSize = 16,
    chunkHeight = 32,
    voxelSizeMetres = 1.0,
    seaLevelMetres = 0.0,
    wrapX = false,
    wrapY = false,
    pipelineVersion = 1L,
    blockPaletteVersion = 1L,
    chunkFormatVersion = 1,
    shapeVersion = 1L,
    createdAt = BORN,
  )

  private var fate: DoomEnding? = null
  private val director = mockk<DoomsdayDirector>(relaxed = true) {
    every { ending() } answers { fate }
    every { status() } returns mockk {
      every { kind } returns DoomKind.VOID_TEAR
      every { tPercent } returns 100.4
    }
  }

  private var stored: StoredWorldEnd? = null
  private val windows = mockk<StoredWorldEndRepository>(relaxed = true) {
    every { findById(StoredWorldEnd.ONLY) } answers { Optional.ofNullable(stored) }
    val saved = slot<StoredWorldEnd>()
    every { save(capture(saved)) } answers { saved.captured.also { stored = it } }
  }
  private val legacy = slot<WorldLegacy>()
  private val legacies = mockk<WorldLegacyRepository> {
    every { save(capture(legacy)) } answers { legacy.captured }
  }
  private val worlds = mockk<WorldRepository> {
    every { save(any()) } answers { firstArg() }
  }
  private val worldService = mockk<WorldService> {
    every { isLoaded } returns true
    every { record } returns world
  }
  private val fates = mockk<SettlementFates> {
    every { fallen() } returns setOf(3, 5)
  }
  private val broadcasts = mutableListOf<SMSG>()
  private val out = mockk<OutMessageProcessor>(relaxed = true) {
    every { sendToAllConnected(capture(broadcasts)) } returns 2
  }
  private val events = mockk<ApplicationEventPublisher>(relaxed = true)
  private val avatarFall = DoomAvatarFall()

  private fun worldEnd(): WorldEnd {
    return WorldEnd(DoomsdayConfig(), director, windows, legacies, worlds, worldService, fates, out, events, avatarFall, clock)
  }

  private fun later(duration: Duration) {
    clock.now = clock.now.plus(duration)
  }

  private fun announcements(): List<Pair<Announcement, List<String>>> {
    return broadcasts.filterIsInstance<AnnouncementSMSG>().map { it.announcement to it.args }
  }

  @Test
  fun `a world whose clock ran out gets a day of Last Stand`() {
    val end = worldEnd()
    end.run()
    assertNull(stored)

    fate = DoomEnding.LOST
    end.run()

    assertEquals(clock.now.plus(Duration.ofHours(24)), stored!!.endsAt)
    assertEquals(listOf(Announcement.LAST_STAND to listOf("24")), announcements())
  }

  @Test
  fun `the countdown is announced once at each mark`() {
    fate = DoomEnding.SAVED
    val end = worldEnd()
    end.run()

    later(Duration.ofMinutes(23 * 60))
    end.run()
    end.run()
    later(Duration.ofMinutes(50))
    end.run()

    val expected = listOf(
      Announcement.CALLING to listOf("24"),
      Announcement.CALLING_SOON to listOf("60"),
      Announcement.CALLING_SOON to listOf("10"),
    )
    assertEquals(expected, announcements())
  }

  @Test
  fun `a restart keeps the countdown without repeating it`() {
    stored = StoredWorldEnd(ending = DoomEnding.LOST, endsAt = clock.now.plus(Duration.ofMinutes(30)), warnedAtMinutes = 60)

    worldEnd().run()

    assertEquals(emptyList<Pair<Announcement, List<String>>>(), announcements())
  }

  @Test
  fun `the final sequence plays once, also for a master who logs in during it`() {
    val end = worldEnd()
    end.open(DoomEnding.LOST, Duration.ofMinutes(2))

    later(Duration.ofSeconds(40))
    end.run()
    end.run()
    later(Duration.ofSeconds(20))
    end.onConnected(AccountConnectedEvent(this, accountId = 9L, authorities = emptySet()))

    val sequence = broadcasts.filterIsInstance<WorldEndSMSG>().single()
    assertEquals(WorldEndSMSG(DoomEnding.LOST, "Genesis", DoomKind.VOID_TEAR, 300, 100, 2, 80), sequence)
    verify { out.sendToPlayer(9L, sequence.copy(secondsLeft = 60)) }
  }

  @Test
  fun `the end goes on record before everyone is sent away`() {
    val end = worldEnd()
    end.open(DoomEnding.SAVED, Duration.ofMinutes(1))
    later(Duration.ofMinutes(1))

    end.run()
    end.run()

    assertEquals(clock.now, world.endedAt)
    assertEquals(DoomEnding.SAVED, legacy.captured.ending)
    assertEquals(DoomKind.VOID_TEAR, legacy.captured.threat)
    assertEquals(2, legacy.captured.townsLost)
    verify(exactly = 1) { worlds.save(world) }
    verify(exactly = 1) { events.publishEvent(WorldEndedEvent(legacy.captured)) }
  }

  @Test
  fun `a preview shows the sequence and ends nothing`() {
    val preview = worldEnd().preview(DoomEnding.SAVED)

    assertEquals(WorldEndSMSG(DoomEnding.SAVED, "Genesis", DoomKind.VOID_TEAR, 300, 100, 2, 90), preview)
    assertNull(stored)
    verify(exactly = 0) { events.publishEvent(any<Any>()) }
  }

  @Test
  fun `a won Last Stand pushes the world back from the brink`() {
    fate = DoomEnding.LOST
    val end = worldEnd()
    end.run()
    fate = null

    assertEquals(DoomEnding.LOST, end.beatBack())
    end.run()

    verify { director.set(0.85) }
    verify { windows.delete(any()) }
    assertEquals(Announcement.LAST_STAND_WON, announcements().last().first)
    assertEquals(1, announcements().count { it.first == Announcement.LAST_STAND })
  }

  @Test
  fun `the Last Stand raises the Avatar, and its fall wins the Last Stand`() {
    fate = DoomEnding.LOST
    val end = worldEnd()
    end.run()
    verify { director.lastStandOpened() }

    avatarFall.onKill(mockk(), KillFact(1L, mockk(), emptySet(), 99, true, EventCreature(DoomAvatarFall.EVENT, 0)))
    fate = null
    end.run()

    verify { director.set(0.85) }
    verify { windows.delete(any()) }
  }

  private companion object {
    val BORN: Instant = Instant.parse("2026-01-01T00:00:00Z")
  }
}

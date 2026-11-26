package net.bestia.zone.doomsday.voidtear

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.persistence.StoredVoidTear
import net.bestia.zone.doomsday.persistence.StoredVoidTearRepository
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.RiftConfig
import net.bestia.zone.worldevent.rift.Rifts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.random.Random

class VoidTearThreatTest {

  private var stored: StoredVoidTear? = null
  private val tears = mockk<StoredVoidTearRepository> {
    every { findById(StoredVoidTear.ONLY) } answers { Optional.ofNullable(stored) }
    val saved = slot<StoredVoidTear>()
    every { save(capture(saved)) } answers { saved.captured.also { stored = it } }
  }

  private val active = mutableListOf<StoredRift>()
  private val ended = HashMap<Long, Phase>()
  private var nextId = 100L
  private val rifts = mockk<Rifts>(relaxed = true) {
    every { active() } answers { active.toList() }
    every { phaseOf(any()) } answers {
      val id = firstArg<Long>()
      active.firstOrNull { it.id == id }?.phase ?: ended[id]
    }
    every { formKnot(any(), any(), any(), any(), any()) } answers {
      riftAt(firstArg(), secondArg(), nextId++).also { it.provinceIndex = thirdArg() }
    }
  }
  private val bestiaClock = mockk<BestiaClock> {
    every { now().absoluteSecond } returns NOW_BESTIA
    every { speedFactor } returns 3.0
  }

  private val threat = VoidTearThreat(
    config = VoidTearConfig(),
    riftConfig = RiftConfig(),
    rifts = rifts,
    tears = tears,
    field = ManaField(),
    ground = mockk(relaxed = true),
    concentration = mockk<ManaConcentrationService>(relaxed = true) { every { densityAt(any()) } returns 1.0 },
    provinces = mockk<ProvinceService>(relaxed = true) { every { provinceAt(any(), any()) } returns null },
    eventGround = mockk<EventGround>(relaxed = true),
    worldService = mockk(relaxed = true),
    bestiaClock = bestiaClock,
    clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC),
    random = Random(1),
  )

  private val onset = DoomFate(originX = 5_000.0, originY = 6_000.0, progress = 0.2, stage = DoomStage.ONSET)

  @Test
  fun `the Tear opens at its origin once the doomsday sets in, and never collapses`() {
    threat.step(onset.copy(stage = DoomStage.OMENS, progress = 0.1), 300.0)
    assertNull(stored!!.riftId)

    threat.step(onset, 300.0)

    val tear = active.single { it.id == stored!!.riftId }
    assertEquals(Phase.OPEN, tear.phase)
    assertEquals(5_000.0, tear.x)
    assertTrue(tear.collapsesAtSecond > NOW_BESTIA + 365L * 86_400)
  }

  @Test
  fun `sealing the Tear holds the clock off and keeps it closed for a while`() {
    threat.step(onset, 300.0)
    end(stored!!.riftId!!, Phase.SEALED)

    assertEquals(-0.15, threat.step(onset, 300.0), 1e-9)
    assertNull(stored!!.riftId)
    assertNotNull(stored!!.reformsAtDay)

    threat.step(onset, 300.0)
    assertNull(stored!!.riftId, "it stays closed until the reform day")
  }

  @Test
  fun `a rift sealed within its reach holds it back, one it swallows brings the end closer`() {
    threat.step(onset, 300.0)
    riftAt(5_500.0, 6_000.0, 7)
    riftAt(8_000.0, 6_000.0, 8)
    threat.step(onset, 300.0)

    end(7, Phase.MERGED)
    end(8, Phase.SEALED)

    assertEquals(0.01, threat.step(onset, 300.0), 1e-9)
    assertEquals(0.2, threat.containment(), 1e-9)
  }

  @Test
  fun `the Tear's stage follows the clock`() {
    assertEquals(1, VoidTearThreat.riftStageOf(0.15))
    assertEquals(3, VoidTearThreat.riftStageOf(0.55))
    assertEquals(5, VoidTearThreat.riftStageOf(0.95))
  }

  private fun riftAt(x: Double, y: Double, id: Long): StoredRift {
    return StoredRift(x = x, y = y, formedAtSecond = 0, opensAtSecond = 0).also {
      it.id = id
      active += it
    }
  }

  private fun end(id: Long, phase: Phase) {
    active.removeIf { it.id == id }
    ended[id] = phase
  }

  private companion object {
    const val NOW_BESTIA = 1_000_000L
  }
}

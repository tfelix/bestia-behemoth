package net.bestia.zone.doomsday.withering

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystal
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystalRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.worldevent.EventGround
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class WitheringThreatTest {

  private val stored = mutableListOf<StoredCorruptionCrystal>()
  private var nextId = 1L
  private val rows = mockk<StoredCorruptionCrystalRepository>(relaxed = true) {
    every { findAll() } answers { stored.toList() }
    val saved = slot<StoredCorruptionCrystal>()
    every { save(capture(saved)) } answers {
      val row = saved.captured
      setId(row, nextId++)
      row.also { stored += it }
    }
    every { deleteAllById(any()) } answers { firstArg<Iterable<Long>>().forEach { id -> stored.removeIf { it.id == id } } }
  }
  private val broken = mutableListOf<Long>()
  private val crystals = mockk<CorruptionCrystals>(relaxed = true) {
    every { takeBroken() } answers { broken.toList().also { broken.clear() } }
  }
  private val field = CorruptionField()
  private val threat = threat(WitheringConfig())

  private fun threat(config: WitheringConfig) = WitheringThreat(
    config = config,
    spread = CorruptionSpread(config, field),
    wards = mockk<WitheringWards> { every { wardedNow() } returns emptySet() },
    journal = mockk(relaxed = true),
    rows = rows,
    crystals = crystals,
    blight = mockk(relaxed = true),
    provinces = mockk<ProvinceService> { every { isSanctuaryAt(any(), any()) } returns false },
    ground = mockk<EventGround> { every { grounded(any(), any()) } returns Vec3L(0, 0, 0) },
    worldService = mockk(),
    random = Random(3),
  )

  private val onset = DoomFate(originX = 20_000.0, originY = 20_000.0, progress = 0.2, stage = DoomStage.ONSET)

  @Test
  fun `nothing withers before onset`() {
    threat.step(onset.copy(stage = DoomStage.OMENS, progress = 0.1), DAY)

    assertEquals(0, stored.size)
    assertEquals(0, field.tileCount)
  }

  @Test
  fun `the first crystal grows at the origin, and the land rots under it`() {
    threat.step(onset, DAY)

    assertEquals(20_000.0, stored.first().x)
    assertTrue(field.intensityAt(20_000.0, 20_000.0) > 0.2)
    assertEquals(0.0, field.intensityAt(30_000.0, 20_000.0))
  }

  @Test
  fun `the front sprouts new crystals at its edge as the clock asks`() {
    val late = onset.copy(progress = 0.6, stage = DoomStage.ESCALATION)
    repeat(10) { threat.step(late, DAY) }

    assertTrue(stored.size > 10, "crystals: ${stored.size}")
    assertTrue(stored.all { hypotTo(it) < 40_000.0 })
    assertTrue(threat.containment() > 0.0, "it cannot keep up with what the clock asks yet")
  }

  @Test
  fun `the land heals once its crystal is broken`() {
    val still = threat(WitheringConfig(crystalsPerDay = 0.0))
    stored += StoredCorruptionCrystal(20_000.0, 20_000.0).also { setId(it, 1) }
    still.step(onset, DAY)
    val rotten = field.intensityAt(20_000.0, 20_000.0)

    broken += 1L
    still.step(onset, DAY)

    assertEquals(0, stored.size)
    assertEquals(rotten - 0.05, field.intensityAt(20_000.0, 20_000.0), 1e-9)
  }

  private fun setId(row: StoredCorruptionCrystal, id: Long) {
    StoredCorruptionCrystal::class.java.getDeclaredField("id").apply { isAccessible = true }.setLong(row, id)
  }

  private fun hypotTo(crystal: StoredCorruptionCrystal): Double {
    return kotlin.math.hypot(crystal.x - onset.originX, crystal.y - onset.originY)
  }

  private companion object {
    const val DAY = 86_400.0
  }
}

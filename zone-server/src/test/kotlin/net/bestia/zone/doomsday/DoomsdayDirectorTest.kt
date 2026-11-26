package net.bestia.zone.doomsday

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.doomsday.persistence.StoredDoomsdayRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.PersistedWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.stream.Stream
import kotlin.random.Random

class DoomsdayDirectorTest {

  private class RecordingThreat : DoomThreat {
    override val kind = DoomKind.VOID_TEAR
    override val shape = DoomShape.ACTOR
    val stages = mutableListOf<Pair<DoomStage, DoomStage>>()
    var stepped = 0.0
    var containment = 0.0

    override fun fits(): Boolean {
      return true
    }

    override fun origin(random: Random): Vec2d {
      return Vec2d(1.0, 2.0)
    }

    override fun containment(): Double {
      return containment
    }

    override fun onStage(from: DoomStage, to: DoomStage) {
      stages += from to to
    }

    override fun step(progress: Double, realSeconds: Double) {
      stepped += realSeconds
    }
  }

  private val threat = RecordingThreat()
  private var stored: StoredDoomsday? = null
  private val repository = mockk<StoredDoomsdayRepository> {
    every { findById(StoredDoomsday.ONLY) } answers { Optional.ofNullable(stored) }
    val saved = slot<StoredDoomsday>()
    every { save(capture(saved)) } answers { saved.captured.also { stored = it } }
  }
  private val world = mockk<PersistedWorld> {
    every { createdAt } returns BORN
    every { seed } returns 42L
  }
  private val worldService = mockk<WorldService> {
    every { isLoaded } returns true
    every { record } returns world
  }
  private val threats = mockk<ObjectProvider<DoomThreat>> {
    every { orderedStream() } answers { Stream.of(threat) }
  }

  private fun director(nowDays: Double = 0.0, config: DoomsdayConfig = DoomsdayConfig()): DoomsdayDirector {
    val now = BORN.plusSeconds((nowDays * 86_400).toLong())
    return DoomsdayDirector(config, repository, worldService, threats, Clock.fixed(now, ZoneOffset.UTC))
  }

  @Test
  fun `a new world gets its fate on the first run`() {
    director().runAt(0.0)

    val fate = stored!!
    assertEquals(DoomKind.VOID_TEAR, fate.kind)
    assertEquals(1.0, fate.originX)
    assertEquals(0.0, fate.progress)
  }

  @Test
  fun `the clock runs after onset, tells the threat of a new stage and lets it act`() {
    stored = StoredDoomsday(kind = DoomKind.VOID_TEAR, onsetDay = 10.0, unfoldDays = 100.0, progress = 0.14)

    director().runAt(15.0)

    assertEquals(0.19, stored!!.progress, 1e-9)
    assertEquals(DoomStage.ONSET, stored!!.stage)
    assertEquals(listOf(DoomStage.OMENS to DoomStage.ONSET), threat.stages)
    assertEquals(15.0 * 86_400, threat.stepped, 1e-6)
  }

  @Test
  fun `players holding the threat slow the clock`() {
    stored = StoredDoomsday(kind = DoomKind.VOID_TEAR, onsetDay = 0.0, unfoldDays = 100.0)
    threat.containment = 0.5

    director().runAt(10.0)

    assertEquals(0.05, stored!!.progress, 1e-9)
  }

  @Test
  fun `a time scale ages the world faster for the doomsday`() {
    val scaled = director(nowDays = 2.0, config = DoomsdayConfig(timeScale = 30.0))

    assertEquals(60.0, scaled.ageDays(), 1e-6)
  }

  @Test
  fun `the status projects the end from the pace`() {
    stored = StoredDoomsday(kind = DoomKind.VOID_TEAR, onsetDay = 100.0, unfoldDays = 200.0, progress = 0.5)
    threat.containment = 0.5

    val status = director(nowDays = 200.0).status()

    assertEquals(200.0 + 0.5 * 200.0 / 0.5, status.projectedEndDay, 1e-6)
    assertTrue(status.tPercent > 66.0)
  }

  private companion object {
    val BORN: Instant = Instant.parse("2026-01-01T00:00:00Z")
  }
}

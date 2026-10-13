package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ground.persistence.GroundLayerMark
import net.bestia.zone.ground.persistence.GroundLayerMarkRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the decay sweep tells clients, which is nothing: they fade their own copy of wear and blood from the
 * clock they were sent. The one exception is a column whose stored levels arrive after the client has already
 * been served it, which the client cannot work out and must be sent at once.
 */
class GroundLevelDecaySystemTest {

  private val chunkSize = 32
  private val shape = 11L
  private val pipeline = 22L

  private val key = ColumnKey.of(0, 0)

  private var clockSecond = 0L

  private val wearConfig = GroundWearConfig()
  private val bloodConfig = GroundBloodConfig()

  private val overlay = mockk<GroundOverlayService>(relaxed = true)

  private fun clock(): BestiaClock {
    return mockk { every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } } }
  }

  private fun repository(rows: List<GroundLayerMark>): GroundLayerMarkRepository {
    return mockk(relaxed = true) {
      every { findByIdColumnKey(any()) } answers { rows.filter { it.id.columnKey == firstArg<Long>() } }
      every { save(any<GroundLayerMark>()) } answers { firstArg() }
    }
  }

  private fun stores(rows: List<GroundLayerMark> = emptyList()): Pair<GroundWearRegistry, GroundBloodRegistry> {
    val repository = repository(rows)

    val worldService = mockk<WorldService> {
      every { config } returns mockk { every { chunkSize } returns this@GroundLevelDecaySystemTest.chunkSize }
      every { record } returns mockk {
        every { shapeVersion } returns shape
        every { pipelineVersion } returns pipeline
      }
    }

    // Inline, so a column read in is in the queue by the time the system looks.
    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    val clock = clock()

    return GroundWearRegistry(repository, executor, worldService, wearConfig, clock) to
        GroundBloodRegistry(repository, executor, worldService, bloodConfig, clock)
  }

  private fun systemOver(wear: GroundWearRegistry, blood: GroundBloodRegistry): GroundLevelDecaySystem {
    return GroundLevelDecaySystem(listOf(wear, blood), overlay, clock())
  }

  private fun row(layer: GroundLayer, level: Int): GroundLayerMark {
    val levels = ColumnLevels(chunkSize)
    levels.add(0, 0, level)

    return GroundLayerMark(GroundLayerMark.Key(key, layer.wireId), levels.toBytes(), 0, shape, pipeline)
  }

  private fun verifyNothingAnnounced() {
    verify(exactly = 0) { overlay.markLayerDirty(any(), any()) }
    verify(exactly = 0) { overlay.markLevelsDirty(any(), any(), any()) }
    verify(exactly = 0) { overlay.markDirty(any()) }
  }

  @Test
  fun `a sweep that fades wear announces nothing`() {
    val (wear, blood) = stores()
    wear.track(key)
    wear.wear(5, 5, 255, 0)
    val sut = systemOver(wear, blood)

    clockSecond = wearConfig.fadeSeconds / 2
    sut.update(testWorld(), 60f)

    val level = assertNotNull(wear.columnAt(key)).levels[5, 5]
    assertTrue(level < 255, "the sweep did not run, so this proves nothing: level is $level")
    verifyNothingAnnounced()
  }

  @Test
  fun `a sweep that fades blood announces nothing`() {
    val (wear, blood) = stores()
    blood.track(key)
    blood.spill(10, 10, 0)
    val sut = systemOver(wear, blood)

    clockSecond = bloodConfig.fadeSeconds / 2
    sut.update(testWorld(), 60f)

    assertTrue(assertNotNull(blood.columnAt(key)).levels[10, 10] < bloodConfig.spillWeight)
    verifyNothingAnnounced()
  }

  @Test
  fun `a sweep that clears a column announces nothing`() {
    val (wear, blood) = stores()
    wear.track(key)
    wear.wear(5, 5, 100, 0)
    val sut = systemOver(wear, blood)

    clockSecond = wearConfig.fadeSeconds
    sut.update(testWorld(), 60f)

    assertTrue(assertNotNull(wear.columnAt(key)).isEmpty, "the sweep did not run, so this proves nothing")
    verifyNothingAnnounced()
  }

  /** The player has been looking at clean ground since they arrived, and nothing the client derives says otherwise. */
  @Test
  fun `stored wear arriving for a held column is announced at once`() {
    val (wear, blood) = stores(listOf(row(GroundLayer.WORN, 200)))
    wear.track(key)
    blood.track(key)
    val sut = systemOver(wear, blood)

    sut.update(testWorld(), 0f)

    assertEquals(200, assertNotNull(wear.columnAt(key)).levels[0, 0])
    verify(exactly = 1) { overlay.markLayerDirty(key, GroundLayer.WORN) }
    verify(exactly = 0) { overlay.markLayerDirty(key, GroundLayer.BLOODIED) }
  }

  @Test
  fun `stored blood arriving for a held column is announced as the bloodied layer`() {
    val (wear, blood) = stores(listOf(row(GroundLayer.BLOODIED, 200)))
    wear.track(key)
    blood.track(key)
    val sut = systemOver(wear, blood)

    sut.update(testWorld(), 0f)

    verify(exactly = 1) { overlay.markLayerDirty(key, GroundLayer.BLOODIED) }
    verify(exactly = 0) { overlay.markLayerDirty(key, GroundLayer.WORN) }
  }

  /** A late snapshot is due now whatever the resync is set to, so it goes through the immediate path. */
  @Test
  fun `a late snapshot does not go through the paced path`() {
    val (wear, blood) = stores(listOf(row(GroundLayer.WORN, 200)))
    wear.track(key)
    val sut = systemOver(wear, blood)

    sut.update(testWorld(), 0f)

    verify(exactly = 0) { overlay.markLevelsDirty(any(), any(), any()) }
  }

  @Test
  fun `a held column with nothing stored announces nothing`() {
    val (wear, blood) = stores()
    wear.track(key)
    blood.track(key)
    val sut = systemOver(wear, blood)

    sut.update(testWorld(), 0f)

    verifyNothingAnnounced()
  }
}

package net.bestia.zone.world.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.environment.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What one footfall leaves behind.
 *
 * The interesting cases are the ones where wear and prints disagree, because that is the whole reason
 * [TrampleableGround] asks two questions instead of one.
 */
class MarkingGroundTrampleTest {

  private val config = GroundWearConfig()

  private val ground = mockk<TrampleableGround>()
  private val wear = mockk<GroundWearRegistry>(relaxed = true)
  private val stamps = mockk<GroundStampRegistry>(relaxed = true)
  private val overlay = mockk<GroundOverlayService>(relaxed = true)

  private val clock = mockk<BestiaClock> {
    every { now() } returns mockk { every { absoluteSecond } returns 500L }
  }

  private val trample = MarkingGroundTrample(ground, wear, stamps, overlay, config, clock)

  private fun groundThat(wears: Double, holdsPrints: Double) {
    every { ground.wearAt(any(), any()) } returns wears
    every { ground.impressionAt(any(), any()) } returns holdsPrints
    every { wear.chunkExtent } returns 32L
  }

  @Test
  fun `snow takes a print and never wears`() {
    groundThat(wears = 0.0, holdsPrints = 1.0)

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(exactly = 0) { wear.wear(any(), any(), any(), any()) }
    verify(exactly = 1) { stamps.stamp(1, 0, GroundStampKind.FOOTPRINT, any(), any(), 1L, 500L) }
  }

  @Test
  fun `paving takes neither`() {
    groundThat(wears = 0.0, holdsPrints = 0.0)

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(exactly = 0) { wear.wear(any(), any(), any(), any()) }
    verify(exactly = 0) { stamps.stamp(any(), any(), any(), any(), any(), any(), any()) }
  }

  /** A print landing in five places at once is not a print, so only wear gets the smear. */
  @Test
  fun `the smear is wear only`() {
    groundThat(wears = 1.0, holdsPrints = 1.0)

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 10, toY = 10)

    verify(exactly = 5) { wear.wear(any(), any(), any(), 500L) }
    verify(exactly = 1) { stamps.stamp(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a step's heading is the eighth it was walked in`() {
    assertEquals(0, MarkingGroundTrample.octantOf(dx = 1, dy = 0))
    assertEquals(1, MarkingGroundTrample.octantOf(dx = 1, dy = 1))
    assertEquals(2, MarkingGroundTrample.octantOf(dx = 0, dy = 1))
    assertEquals(4, MarkingGroundTrample.octantOf(dx = -1, dy = 0))
    assertEquals(6, MarkingGroundTrample.octantOf(dx = 0, dy = -1))
    assertEquals(7, MarkingGroundTrample.octantOf(dx = 1, dy = -1))
  }

  @Test
  fun `a step that went nowhere still has a heading the wire can carry`() {
    assertTrue(MarkingGroundTrample.octantOf(dx = 0, dy = 0) in 0..7)
  }

  /** A teleport is still a direction, and the wire only has room for eight of them. */
  @Test
  fun `a jump of several tiles still reduces to an octant`() {
    assertEquals(2, MarkingGroundTrample.octantOf(dx = 0, dy = 40))
    assertTrue(MarkingGroundTrample.octantOf(dx = -17, dy = -3) in 0..7)
  }

  @Test
  fun `a seed fits the byte the wire gives it`() {
    for (x in -3L..3L) {
      for (y in -3L..3L) {
        assertTrue(MarkingGroundTrample.seedOf(entityId = 77, voxelX = x, voxelY = y) in 0..255)
      }
    }
  }

  /**
   * Two creatures on one trail have to leave two lines of tracks rather than one line walked twice, which is
   * the only thing the seed is for.
   */
  @Test
  fun `two walkers over the same tile get different prints`() {
    val walkers = (1L..32L).map { MarkingGroundTrample.seedOf(it, voxelX = 4, voxelY = 9) }

    assertTrue(walkers.distinct().size > walkers.size / 2, "seeds clumped: $walkers")
  }

  @Test
  fun `a print re-sent later is still the same print`() {
    assertEquals(
      MarkingGroundTrample.seedOf(entityId = 5, voxelX = 100, voxelY = -20),
      MarkingGroundTrample.seedOf(entityId = 5, voxelX = 100, voxelY = -20)
    )
  }
}

package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.ground.persistence.GroundLayerMarkRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

  /**
   * **Cross-language fixture.** Computed independently with 64-bit BigInt arithmetic and mirrored literally in
   * the client's `GroundFootfallTest`. A client that disagrees draws every print facing the wrong way and
   * nothing throws, so these values are never regenerated from this code.
   */
  @Test
  fun `the octant of a step is exactly what the client is promised`() {
    val expected = listOf(
      Triple(1L, 0L, 0), Triple(1L, 1L, 1), Triple(0L, 1L, 2), Triple(-1L, 1L, 3),
      Triple(-1L, 0L, 4), Triple(-1L, -1L, 5), Triple(0L, -1L, 6), Triple(1L, -1L, 7),
      Triple(0L, 0L, 0), Triple(0L, 40L, 2), Triple(-17L, -3L, 4), Triple(5L, -2L, 0),
    )

    for ((dx, dy, octant) in expected) {
      assertEquals(octant, MarkingGroundTrample.octantOf(dx, dy), "octantOf($dx, $dy)")
    }
  }

  /** The other half of the fixture above: negative tiles, a huge entity id, and a tile past 32 bits. */
  @Test
  fun `the seed of a print is exactly what the client is promised`() {
    val expected = listOf(
      Triple(1L, 0L to 0L, 120),
      Triple(77L, 3L to -3L, 217),
      Triple(77L, -3L to 3L, 96),
      Triple(5L, 100L to -20L, 58),
      Triple(5L, -100L to 20L, 92),
      Triple(1_234_567_890_123L, -1L to -1L, 51),
      Triple(9_000_000_000_000_000_000L, 93_287L to 36_927L, 210),
      Triple(-1L, 0L to 0L, 58),
      Triple(42L, -2_147_483_649L to 2_147_483_648L, 227),
      Triple(1L, 4L to 9L, 213),
      Triple(2L, 4L to 9L, 138),
      Triple(3L, 4L to 9L, 175),
    )

    for ((entityId, tile, seed) in expected) {
      assertEquals(seed, MarkingGroundTrample.seedOf(entityId, tile.first, tile.second), "seedOf($entityId, $tile)")
    }
  }

  /** What the overlay is handed is what makes the client's resync arrive, so it must name the right column. */
  @Test
  fun `a print queues its column for a resync`() {
    groundThat(wears = 0.0, holdsPrints = 1.0)
    every { stamps.stamp(any(), any(), any(), any(), any(), any(), any()) } returns ColumnKey.of(2, 3)

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(exactly = 1) { overlay.markStampsDirty(ColumnKey.of(2, 3), 500L) }
  }

  @Test
  fun `a print the full store refused queues nothing`() {
    groundThat(wears = 0.0, holdsPrints = 1.0)
    every { stamps.stamp(any(), any(), any(), any(), any(), any(), any()) } returns null

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(exactly = 0) { overlay.markStampsDirty(any(), any()) }
  }

  @Test
  fun `wear that changes what is drawn queues the worn layer for a resync`() {
    groundThat(wears = 1.0, holdsPrints = 0.0)
    every { wear.wear(any(), any(), any(), any()) } returns true
    every { wear.columnKeyOf(any(), any()) } returns ColumnKey.of(4, 5)

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(atLeast = 1) { overlay.markLevelsDirty(ColumnKey.of(4, 5), GroundLayer.WORN, 500L) }
    verify(exactly = 0) { overlay.markLayerDirty(any(), any()) }
  }

  @Test
  fun `wear that is still below notice queues nothing`() {
    groundThat(wears = 1.0, holdsPrints = 0.0)
    every { wear.wear(any(), any(), any(), any()) } returns false

    trample.steppedOn(entityId = 1, fromX = 0, fromY = 0, toX = 1, toY = 0)

    verify(exactly = 0) { overlay.markLevelsDirty(any(), any(), any()) }
  }

  /**
   * **Cross-language fixture.** The client applies these same amounts to its shadow grid, so a step onto each
   * ground has to leave exactly these levels - read from a real store rather than from what was asked of a mock,
   * because a neighbour whose amount truncates to nothing must leave its cell untouched.
   */
  @Test
  fun `a step wears the tile and its neighbours by exactly what the client is promised`() {
    // block -> level at the centre, then at each of the four neighbours.
    val expected = mapOf(
      BlockType.GRASS to (6 to 2),
      BlockType.DRY_GRASS to (4 to 1),
      BlockType.BLIGHTED_GRASS to (5 to 1),
      BlockType.MUD to (3 to 1),
      BlockType.DIRT to (2 to 0),
    )

    for ((block, levels) in expected) {
      val registry = realWearRegistry()
      stepOnto(registry, uniform(block))

      val column = assertNotNull(registry.columnAt(ColumnKey.of(0, 0))).levels

      assertEquals(levels.first, column[10, 10], "$block under the foot")
      for ((x, y) in listOf(11 to 10, 9 to 10, 10 to 11, 10 to 9)) {
        assertEquals(levels.second, column[x, y], "$block beside the foot at $x,$y")
      }
      assertEquals(0, column[11, 11], "$block on a diagonal, which is not smeared")
    }
  }

  /** Each neighbour is judged on its own block, which is what stops a trail beside a road smearing onto it. */
  @Test
  fun `a neighbour is worn by its own ground, not by the ground under the foot`() {
    val registry = realWearRegistry()
    val surface = mockk<SurfaceBlockLookup> {
      every { blockAt(any(), any()) } answers { if (firstArg<Long>() == 11L) BlockType.COBBLESTONE else BlockType.GRASS }
    }

    stepOnto(registry, surface)

    val column = assertNotNull(registry.columnAt(ColumnKey.of(0, 0))).levels

    assertEquals(6, column[10, 10])
    assertEquals(0, column[11, 10], "the paving beside the foot was worn")
    assertEquals(2, column[9, 10])
  }

  private fun uniform(block: BlockType): SurfaceBlockLookup {
    return mockk { every { blockAt(any(), any()) } returns block }
  }

  private fun stepOnto(registry: GroundWearRegistry, surface: SurfaceBlockLookup) {
    val sut = MarkingGroundTrample(SurfaceTrampleableGround(surface), registry, stamps, overlay, config, clock)

    sut.steppedOn(entityId = 1, fromX = 9, fromY = 10, toX = 10, toY = 10)
  }

  private fun realWearRegistry(): GroundWearRegistry {
    val repository = mockk<GroundLayerMarkRepository>(relaxed = true) {
      every { findByIdColumnKey(any()) } returns emptyList()
    }

    val worldService = mockk<WorldService> {
      every { this@mockk.config } returns mockk { every { chunkSize } returns 32 }
      every { record } returns mockk {
        every { shapeVersion } returns 1L
        every { pipelineVersion } returns 1L
      }
    }

    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    return GroundWearRegistry(repository, executor, worldService, config, clock).apply {
      track(ColumnKey.of(0, 0))
    }
  }
}

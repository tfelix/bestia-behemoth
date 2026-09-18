package net.bestia.zone.world.ground

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The stamp store: where it records tracks, how fast it will talk about them, and when it forgets them.
 *
 * The pacing is the design property here rather than an optimisation. Each message carries a column's whole
 * stamp set, so a registry that announced every footfall would re-send half a kilobyte twenty times a second
 * behind one walker.
 */
class GroundStampRegistryTest {

  private val chunkSize = 32
  private val config = GroundStampConfig(maxStampsPerColumn = 8, maxColumns = 4)

  private var clockSecond = 0L

  private fun registry(config: GroundStampConfig = this.config): GroundStampRegistry {
    val worldService = mockk<WorldService> {
      every { this@mockk.config } returns mockk {
        every { this@mockk.chunkSize } returns this@GroundStampRegistryTest.chunkSize
      }
    }

    val clock = mockk<BestiaClock> {
      every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } }
    }

    return GroundStampRegistry(config, worldService, clock)
  }

  private fun GroundStampRegistry.walk(
    voxelX: Long,
    voxelY: Long,
    atSecond: Long = clockSecond,
    actorId: Long = 1,
  ) {
    stamp(voxelX, voxelY, GroundStampKind.FOOTPRINT, octant = 2, seed = 7, actorId = actorId, nowSecond = atSecond)
  }

  @Test
  fun `a footfall lands in the column that holds the tile`() {
    val sut = registry()

    sut.walk(voxelX = 35, voxelY = 70)

    assertEquals(1, sut.stampedColumns)
    assertNotNull(sut.stampsAt(ColumnKey.of(1, 2)))
    assertNull(sut.stampsAt(ColumnKey.of(0, 0)))
  }

  @Test
  fun `negative coordinates land in the column below the origin, not beside it`() {
    val sut = registry()

    sut.walk(voxelX = -1, voxelY = -1)

    assertNotNull(sut.stampsAt(ColumnKey.of(-1, -1)))
  }

  /**
   * **The departure from wear, and it is deliberate.** Wear refuses to accumulate where nobody is holding the
   * ground. Tracks are the opposite: something leaving tracks where nobody is watching is the whole premise of
   * following them afterwards.
   */
  @Test
  fun `tracks are recorded where nobody is watching`() {
    val sut = registry()

    sut.walk(voxelX = 5_000, voxelY = 5_000)

    assertNotNull(sut.stampsAt(ColumnKey.of(156, 156)))
  }

  @Test
  fun `a walked column is announced once, not once per footfall`() {
    val sut = registry()

    sut.walk(1, 1)
    assertEquals(listOf(ColumnKey.of(0, 0)), sut.sweep(nowSecond = 0))

    sut.walk(2, 2)
    sut.walk(3, 3)
    assertEquals(emptyList(), sut.sweep(nowSecond = 1), "the column was announced again inside its interval")

    assertEquals(
      listOf(ColumnKey.of(0, 0)),
      sut.sweep(nowSecond = config.announceIntervalSeconds),
      "the column was never announced again after its interval"
    )
  }

  @Test
  fun `a column nothing has happened to is not announced at all`() {
    val sut = registry()

    sut.walk(1, 1)
    sut.sweep(nowSecond = 0)

    assertEquals(emptyList(), sut.sweep(nowSecond = config.announceIntervalSeconds + 1))
  }

  /** The empty message is what retires the tracks on the client, so it must go out before the column does. */
  @Test
  fun `a column whose last print expires is announced and only then forgotten`() {
    val sut = registry()

    sut.walk(1, 1, atSecond = 0)
    sut.sweep(nowSecond = 0)

    val expired = config.footprintTtlSeconds

    assertEquals(listOf(ColumnKey.of(0, 0)), sut.sweep(nowSecond = expired))
    assertEquals(0, sut.stampedColumns)
    assertNull(sut.stampsAt(ColumnKey.of(0, 0)))
  }

  @Test
  fun `the store is bounded by columns, not by how far anything walks`() {
    val sut = registry()

    for (chunk in 0 until config.maxColumns + 5) {
      sut.walk(voxelX = chunk * chunkSize.toLong(), voxelY = 0)
    }

    assertEquals(config.maxColumns, sut.stampedColumns)
  }

  @Test
  fun `room freed by expiry is usable again`() {
    val oneColumn = GroundStampConfig(maxColumns = 1)
    val sut = registry(oneColumn)

    sut.walk(voxelX = 0, voxelY = 0, atSecond = 0)
    sut.walk(voxelX = 1_000, voxelY = 0, atSecond = 0)
    assertEquals(1, sut.stampedColumns, "the second column was recorded despite the cap")

    sut.sweep(nowSecond = 0)
    sut.sweep(nowSecond = oneColumn.footprintTtlSeconds)

    sut.walk(voxelX = 1_000, voxelY = 0, atSecond = oneColumn.footprintTtlSeconds)

    assertNotNull(sut.stampsAt(ColumnKey.of(31, 0)), "the freed slot was never reused")
  }

  @Test
  fun `the wire form ages with the clock rather than with the call`() {
    val sut = registry()

    clockSecond = 0
    sut.walk(voxelX = 1, voxelY = 1, atSecond = 0)
    val fresh = sut.stampsAt(ColumnKey.of(0, 0))!!.last()

    clockSecond = config.footprintTtlSeconds / 2
    val older = sut.stampsAt(ColumnKey.of(0, 0))!!.last()

    assertTrue(
      (older.toInt() and 0xFF) < (fresh.toInt() and 0xFF),
      "a print that has been there half its life is drawn as strongly as a fresh one"
    )
  }
}

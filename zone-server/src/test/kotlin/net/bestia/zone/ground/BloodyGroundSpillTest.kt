package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test

/** A death queues what it soaked for a resync, because the client lays the same pool when it sees the death. */
class BloodyGroundSpillTest {

  private val registry = mockk<GroundBloodRegistry>()
  private val overlay = mockk<GroundOverlayService>(relaxed = true)

  private val clock = mockk<BestiaClock> {
    every { now() } returns mockk { every { absoluteSecond } returns 700L }
  }

  private val spill = BloodyGroundSpill(registry, overlay, clock)

  @Test
  fun `each column the pool reached is queued as the bloodied layer`() {
    every { registry.spill(31, 10, 700L) } returns setOf(ColumnKey.of(0, 0), ColumnKey.of(1, 0))

    spill.bledAt(31, 10)

    verify(exactly = 1) { overlay.markLevelsDirty(ColumnKey.of(0, 0), GroundLayer.BLOODIED, 700L) }
    verify(exactly = 1) { overlay.markLevelsDirty(ColumnKey.of(1, 0), GroundLayer.BLOODIED, 700L) }
    verify(exactly = 0) { overlay.markLayerDirty(any(), any()) }
  }

  @Test
  fun `a pool nobody can see queues nothing`() {
    every { registry.spill(any(), any(), any()) } returns emptySet()

    spill.bledAt(10, 10)

    verify(exactly = 0) { overlay.markLevelsDirty(any(), any(), any()) }
  }
}

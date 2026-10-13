package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test

/** The system only lets prints expire; telling clients about them is the overlay's business. */
class GroundStampSystemTest {

  private val registry = mockk<GroundStampRegistry>(relaxed = true)

  private val clock = mockk<BestiaClock> {
    every { now() } returns mockk { every { absoluteSecond } returns 4_321L }
  }

  @Test
  fun `each tick expires prints against the clock`() {
    val sut = GroundStampSystem(registry, clock)

    sut.update(testWorld(), 0.05f)

    verify(exactly = 1) { registry.sweep(4_321L) }
  }
}

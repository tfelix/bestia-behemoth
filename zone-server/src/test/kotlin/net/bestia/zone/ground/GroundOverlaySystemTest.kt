package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test

class GroundOverlaySystemTest {

  private val overlay = mockk<GroundOverlayService>(relaxed = true)

  private val sut = GroundOverlaySystem(overlay)

  @Test
  fun `a tick with nothing owed does not flush`() {
    every { overlay.pending } returns 0

    sut.update(testWorld(), 0.05f)

    verify(exactly = 0) { overlay.flush() }
  }

  /** The overlay decides what has fallen due, so columns waiting out a resync must not stop the flush being asked. */
  @Test
  fun `a tick with columns waiting flushes and lets the overlay decide`() {
    every { overlay.pending } returns 3

    sut.update(testWorld(), 0.05f)

    verify(exactly = 1) { overlay.flush() }
  }
}

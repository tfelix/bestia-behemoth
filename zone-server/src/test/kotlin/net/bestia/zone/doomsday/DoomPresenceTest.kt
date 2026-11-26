package net.bestia.zone.doomsday

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoomPresenceTest {

  @Test
  fun `omens are felt only close to the origin`() {
    assertEquals(0.3, DoomPresence.at(0.0, DoomStage.OMENS, 0.0), 1e-9)
    assertEquals(0.0, DoomPresence.at(10_000.0, DoomStage.OMENS, 0.0), 1e-9)
  }

  @Test
  fun `the reach grows with the clock`() {
    val early = DoomPresence.at(20_000.0, DoomStage.ESCALATION, 0.4)
    val late = DoomPresence.at(20_000.0, DoomStage.ESCALATION, 0.8)

    assertTrue(late > early)
  }

  @Test
  fun `the cataclysm is felt everywhere`() {
    assertEquals(0.45, DoomPresence.at(1_000_000.0, DoomStage.CATACLYSM, 1.0), 1e-9)
    assertEquals(1.0, DoomPresence.at(0.0, DoomStage.CATACLYSM, 1.0), 1e-9)
  }
}

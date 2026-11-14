package net.bestia.zone.townsfolk.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals

class CertaintyTest {

  @Test
  fun `what somebody alive saw is firsthand`() {
    assertEquals(Certainty.FIRSTHAND, Certainty.of(Era.RECENT, handedDown = false))
    assertEquals(Certainty.FIRSTHAND, Certainty.of(Era.LIVING, handedDown = false))
  }

  @Test
  fun `a few generations back it was told, and before that it is a saying`() {
    assertEquals(Certainty.TOLD, Certainty.of(Era.GENERATIONS, handedDown = false))
    assertEquals(Certainty.LEGEND, Certainty.of(Era.DISTANT, handedDown = false))
    assertEquals(Certainty.LEGEND, Certainty.of(Era.ANCIENT, handedDown = false))
  }

  @Test
  fun `a family keeps its own story as something told, however old`() {
    assertEquals(Certainty.TOLD, Certainty.of(Era.ANCIENT, handedDown = true))
  }
}

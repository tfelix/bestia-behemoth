package net.bestia.zone.battle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ElementTest {

  @Test
  fun `the base of a levelled element drops its level`() {
    assertEquals(Element.FIRE, Element.FIRE_3.base)
    assertEquals(Element.UNDEAD, Element.UNDEAD_4.base)
    assertEquals(Element.NORMAL, Element.NORMAL.base)
  }
}

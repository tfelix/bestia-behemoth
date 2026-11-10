package net.bestia.zone.fishing

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrikeTimingCheckTest {

  private val check = StrikeTimingCheck()

  @Test
  fun `a human's strikes are slow enough and uneven`() {
    val human = listOf(240L, 310, 205, 280, 350, 260, 225, 300, 270, 330, 215, 290, 245, 320, 265, 235, 380, 255, 285, 230)

    assertFalse(check.looksScripted(human))
  }

  @Test
  fun `strikes faster than a human look scripted`() {
    assertTrue(check.looksScripted(List(20) { 60L + it * 7 }))
  }

  @Test
  fun `strikes that never vary look scripted even at a human pace`() {
    assertTrue(check.looksScripted(List(20) { 250L + it % 3 }))
  }
}

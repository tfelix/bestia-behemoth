package net.bestia.zone.casting

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CastTimeTest {

  @Test
  fun `willpower shortens a cast, a third at 50 WIL`() {
    assertEquals(3f, CastTime.seconds(3f, willpower = 0, reductionPercent = 0))
    assertEquals(2f, CastTime.seconds(3f, willpower = 50, reductionPercent = 0), 0.001f)
  }

  @Test
  fun `gear takes its share off what willpower left`() {
    assertEquals(1f, CastTime.seconds(3f, willpower = 50, reductionPercent = 50), 0.001f)
  }

  @Test
  fun `a fifth of the cast always remains`() {
    assertEquals(0.6f, CastTime.seconds(3f, willpower = 300, reductionPercent = 90), 0.001f)
  }
}

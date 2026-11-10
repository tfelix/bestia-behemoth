package net.bestia.zone.breeding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

class BreedingTimesTest {

  @Test
  fun `a pair of level ten breeds in about two and three quarter hours`() {
    val time = BreedingTimes.breeding(10, 10)

    assertTrue(time > Duration.ofMinutes(165) && time < Duration.ofMinutes(167), "$time")
  }

  @Test
  fun `a pair of level ten hatches in 27 to 30 minutes`() {
    val random = Random(3)
    repeat(100) {
      val time = BreedingTimes.hatching(10, 10, random)
      assertTrue(time >= Duration.ofSeconds(1620) && time <= Duration.ofSeconds(1800), "$time")
    }
  }

  @Test
  fun `reductions shorten a timer to no less than half`() {
    val base = Duration.ofSeconds(1000)

    assertEquals(Duration.ofSeconds(750), BreedingTimes.reduced(base, 25))
    assertEquals(Duration.ofSeconds(500), BreedingTimes.reduced(base, 80))
  }
}

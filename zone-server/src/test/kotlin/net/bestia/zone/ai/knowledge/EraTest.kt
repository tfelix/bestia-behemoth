package net.bestia.zone.ai.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where one band ends and the next begins.
 *
 * Boundaries rather than samples, because an off-by-one here is invisible: every year still lands in
 * some band and still reads as a sentence, so the only thing that would ever show it is a test.
 */
class EraTest {

  @Test
  fun `each band runs up to and including its own bound`() {
    assertEquals(Era.RECENT, Era.of(20))
    assertEquals(Era.LIVING, Era.of(21))
    assertEquals(Era.LIVING, Era.of(80))
    assertEquals(Era.GENERATIONS, Era.of(81))
    assertEquals(Era.GENERATIONS, Era.of(200))
    assertEquals(Era.DISTANT, Era.of(201))
    assertEquals(Era.DISTANT, Era.of(500))
    assertEquals(Era.ANCIENT, Era.of(501))
  }

  @Test
  fun `the last band is unbounded, so every year lands somewhere`() {
    assertEquals(Era.ANCIENT, Era.of(Int.MAX_VALUE))
  }

  @Test
  fun `an event dated after the present is a bug, not a prophecy`() {
    // Clamped rather than refused: the band a bad year lands in should not depend on how bad it is.
    assertEquals(Era.RECENT, Era.of(0))
    assertEquals(Era.RECENT, Era.of(-40))
  }

  @Test
  fun `every band has a key of its own`() {
    val keys = Era.entries.map { it.key }

    assertEquals(Era.entries.size, keys.toSet().size, "two bands share a key: $keys")
    assertEquals(listOf("ERA_RECENT", "ERA_LIVING", "ERA_GENERATIONS", "ERA_DISTANT", "ERA_ANCIENT"), keys)
  }
}

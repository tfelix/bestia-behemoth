package net.bestia.zone.spawn

import net.bestia.zone.bestia.persistence.Bestia
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelRollTest {

  private fun species(min: Int, max: Int): Bestia {
    return Bestia(id = 1, identifier = "rat", levelMin = min, levelMax = max, experienceReward = 4, health = 10, mana = 5)
  }

  @Test
  fun `a species with one level always rolls it`() {
    val rolls = (1..100).map { LevelRoll.roll(species(7, 7), Random.Default) }

    assertEquals(setOf(7), rolls.toSet())
  }

  @Test
  fun `every roll lies inside the range and both ends are reachable`() {
    val random = Random(42)

    val rolls = (1..2000).map { LevelRoll.roll(species(1, 4), random) }

    assertTrue(rolls.all { it in 1..4 })
    assertEquals(setOf(1, 2, 3, 4), rolls.toSet())
  }

  @Test
  fun `the same seed rolls the same level`() {
    val first = LevelRoll.roll(species(50, 58), Random(7))
    val second = LevelRoll.roll(species(50, 58), Random(7))

    assertEquals(first, second)
  }
}

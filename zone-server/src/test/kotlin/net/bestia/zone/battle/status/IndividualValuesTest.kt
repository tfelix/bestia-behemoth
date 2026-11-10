package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class IndividualValuesTest {

  @Test
  fun `a wild roll covers the whole range`() {
    val random = Random(7)
    val rolled = (1..500).flatMap { IndividualValues.rollWild(random).let { ivs -> IvStat.entries.map(ivs::get) } }

    assertTrue(rolled.all { it in 0..100 })
    assertEquals(0, rolled.min())
    assertEquals(100, rolled.max())
  }

  @Test
  fun `a value outside 0 to 100 is refused`() {
    assertThrows<IllegalArgumentException> { IndividualValues(hp = 101) }
  }

  @Test
  fun `of builds every stat from one function`() {
    val ivs = IndividualValues.of { it.ordinal * 10 }

    assertEquals(IvStat.entries.map { it.ordinal * 10 }, IvStat.entries.map(ivs::get))
  }
}

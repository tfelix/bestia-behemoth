package net.bestia.zone.fishing

import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatchTableTest {

  private val boot = CatchTable.Entry(itemId = 1, level = 1, weight = 40)
  private val perch = CatchTable.Entry(itemId = 2, level = 8, weight = 100)
  private val eel = CatchTable.Entry(itemId = 3, level = 32, weight = 100)
  private val table = CatchTable(listOf(boot, perch, eel), overCapHalvingLevels = 5)

  @Test
  fun `an entry up to the cap keeps its weight`() {
    assertEquals(100.0, table.weightOf(perch, levelCap = 8))
  }

  @Test
  fun `above the cap the weight halves every five levels`() {
    assertEquals(25.0, table.weightOf(perch, levelCap = -2))
    assertEquals(100.0 * Math.pow(0.5, 22 / 5.0), table.weightOf(eel, levelCap = 10), 1e-9)
  }

  @Test
  fun `the rod's max catch level keeps higher entries from ever biting`() {
    val random = Random(42)
    val drawn = (1..2_000).mapNotNull { table.draw(maxCatchLevel = 25, levelCap = 100, random) }

    assertEquals(setOf(boot, perch), drawn.toSet())
  }

  @Test
  fun `a rod below every entry catches nothing`() {
    assertNull(table.draw(maxCatchLevel = 0, levelCap = 100, Random(1)))
  }

  @Test
  fun `the draw follows the weights`() {
    val draws = 20_000
    val random = Random(42)
    val perches = (1..draws).count { table.draw(maxCatchLevel = 100, levelCap = 100, random) == perch }

    // 100 of 240 weight.
    assertEquals(100.0 / 240.0, perches / draws.toDouble(), 0.02)
  }
}

package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HunterPassivesTest {

  @Test
  fun `Camouflage cuts the range the wild notices the master at by a tenth per level`() {
    assertEquals(listOf(10, 20, 30), (1..3).map { level -> recalc { Camouflage().apply(it, level) }.noticeRangeCutPercent })
  }

  private fun recalc(apply: (StatusValueRecalcContext) -> Unit): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also(apply)
  }
}

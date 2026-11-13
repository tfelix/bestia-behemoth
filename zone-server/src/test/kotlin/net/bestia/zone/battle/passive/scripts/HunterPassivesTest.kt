package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HunterPassivesTest {

  @Test
  fun `Camouflage cuts the range the wild notices the master at by a tenth per level`() {
    assertEquals(listOf(10, 20, 30), (1..3).map { level -> recalc { Camouflage().apply(it, level) }.noticeRangeCutPercent })
  }

  @Test
  fun `Trailblazer quickens the walk 3 percent per level off the roads only`() {
    val offRoad = recalc { it.walksOffRoad = true; Trailblazer().apply(it, 5) }
    val onRoad = recalc { Trailblazer().apply(it, 5) }

    assertEquals(11.5f, offRoad.speed, 0.001f)
    assertEquals(10f, onRoad.speed)
    assertEquals(15, onRoad.offRoadSpeedPercent, "the ground is watched on the road too")
  }

  @Test
  fun `Pack Hunt makes the bestias 10 percent a level fiercer against the prey, and not the master`() {
    assertEquals(30, recalc { PackHunt().applyToBestia(it, 3) }.markedPreyDamagePercent)
    assertEquals(0, recalc { PackHunt().apply(it, 3) }.markedPreyDamagePercent)
    assertTrue(PackHunt().reachesOwnedBestias)
  }

  private fun recalc(apply: (StatusValueRecalcContext) -> Unit): StatusValueRecalcContext {
    return StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f).also(apply)
  }
}

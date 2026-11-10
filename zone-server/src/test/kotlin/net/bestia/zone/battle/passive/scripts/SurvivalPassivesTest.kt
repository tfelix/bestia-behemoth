package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurvivalPassivesTest {

  private fun context() = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

  @Test
  fun `quick travel adds three percent speed per level and reaches bestias`() {
    val context = context()
    QuickTravel().apply(context, level = 5)

    assertEquals(11.5f, context.speed, 1e-4f)
    assertTrue(QuickTravel().reachesOwnedBestias)
  }

  @Test
  fun `carry capacity adds fifteen percent per level`() {
    val context = context()
    MaximizeCarryCapacity().apply(context, level = 3)

    assertEquals(45, context.carryCapacityPercent)
  }

  @Test
  fun `expert taming spares the bestias stamina and does nothing for the master`() {
    val master = context()
    val bestia = context()
    ExpertTaming().apply(master, level = 5)
    ExpertTaming().applyToBestia(bestia, level = 5)

    assertEquals(0, master.staminaDrainReduction)
    assertEquals(10, bestia.staminaDrainReduction)
    assertEquals(10, bestia.staminaCostReduction)
  }
}

package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreeCastTest {

  @Test
  fun `each level keeps more speed while casting and resists more interrupts`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

    FreeCast().apply(context, 3)

    assertEquals(60, context.castMovePercent)
    assertEquals(30, context.castInterruptResistance)
  }
}

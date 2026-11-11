package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DemonBaneTest {

  @Test
  fun `each level deals five percent more to the unholy and takes six percent less from them`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

    DemonBane().apply(context, 3)

    assertEquals(15, context.damageModifiers.dealtVsUnholy)
    assertEquals(18, context.damageModifiers.takenFromUnholy)
  }
}

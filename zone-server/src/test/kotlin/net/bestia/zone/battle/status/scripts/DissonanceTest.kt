package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DissonanceTest {

  @Test
  fun `Dissonance takes 5 percent ASPD and 3 percent HIT a level for 20 s`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

    Dissonance().apply(testWorld(), 1L, context, ActiveStatusEffect(StatusEffectId.DISSONANCE.id, 5, 20f))

    assertEquals(-25, context.aspdPercent)
    assertEquals(-15, context.hitPercent)
    assertEquals(20.0, Dissonance().durationSeconds(5))
  }
}

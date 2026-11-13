package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImprovedConcentrationTest {

  @Test
  fun `Improve Concentration adds 2 AGI and 2 DEX a level for a minute`() {
    val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)

    ImprovedConcentration().apply(testWorld(), 1L, context, ActiveStatusEffect(StatusEffectId.IMPROVE_CONCENTRATION.id, 5, 60f))

    assertEquals(11, context.agility)
    assertEquals(11, context.dexterity)
    assertEquals(60.0, ImprovedConcentration().durationSeconds(5))
  }
}

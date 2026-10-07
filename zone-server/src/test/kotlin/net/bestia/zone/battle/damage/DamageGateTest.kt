package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class DamageGateTest {

  private val world = testWorld()

  @Test
  fun `an effect shielding from all harm turns every blow away`() {
    val shielded = entityWith(ActiveStatusEffect(definitionId = 1L, level = 1, remainingSeconds = 10f, shield = HarmShield.ALL))

    assertEquals(DamageGate.Verdict.IMMUNE, DamageGate.verdict(world, ATTACKER, shielded))
  }

  @Test
  fun `an effect without a shield lets it land`() {
    val ordinary = entityWith(ActiveStatusEffect(definitionId = 1L, level = 1, remainingSeconds = 10f))

    assertEquals(DamageGate.Verdict.ADMITTED, DamageGate.verdict(world, ATTACKER, ordinary))
  }

  private fun entityWith(effect: ActiveStatusEffect): EntityId {
    return world.createEntity { id -> world.add(id, StatusEffects(mutableListOf(effect))) }
  }

  private companion object {
    const val ATTACKER = 999L
  }
}

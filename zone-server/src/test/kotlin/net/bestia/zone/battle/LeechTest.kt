package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals

class LeechTest {

  private val world = testWorld()
  private val statusEffects = StatusEffectService(StatusEffectDefinitionRegistry(), StatusEffectScriptRegistry(emptyList()))

  @Test
  fun `a leech essence gives back its share of the damage dealt`() {
    val dealer = world.createEntity { id ->
      add(id, Health(100, 1000))
      add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(leechPermille = 32)))
    }

    statusEffects.onDamageDealt(world, dealer, 1000)

    assertEquals(132, world.get(dealer, Health::class)!!.current)
  }
}

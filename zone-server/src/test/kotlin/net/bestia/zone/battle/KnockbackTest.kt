package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class KnockbackTest {

  private val world = testWorld()

  @Test
  fun `a push goes its full length, not at all against an immune body, and half as far against a steady one`() {
    val plain = fighter(CombatValues.OBJECT_BASELINE, health = 100)
    val juggernaut = fighter(CombatValues.OBJECT_BASELINE.copy(knockbackImmune = true), health = 100)
    val steady = fighter(CombatValues.OBJECT_BASELINE.copy(halvesStunAndKnockback = true), health = 100)
    val shaken = fighter(CombatValues.OBJECT_BASELINE.copy(halvesStunAndKnockback = true), health = 40)

    assertEquals(listOf(4, 0, 2, 4), listOf(plain, juggernaut, steady, shaken).map { Knockback.tilesFor(world, it, 4) })
    assertEquals(0, Knockback.tilesFor(world, steady, 1), "half a tile is no push")
  }

  private fun fighter(values: CombatValues, health: Int): EntityId {
    return world.createEntity { id ->
      add(id, CombatStats(values))
      add(id, Health(current = health, max = 100))
    }
  }
}

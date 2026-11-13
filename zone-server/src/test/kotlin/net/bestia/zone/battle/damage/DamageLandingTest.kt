package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DamageLandingTest {

  private val world = testWorld()
  private val sword = HitKind.physical(HitReach.MELEE)

  private val hunter = world.createEntity { id ->
    add(id, Master(HUNTER_MASTER_ID))
    add(id, fighter(markedPreyDamagePercent = 0))
  }
  private val prey = world.createEntity { id -> add(id, fighter(markedPreyDamagePercent = 0)) }
  private val bystander = world.createEntity { id -> add(id, fighter(markedPreyDamagePercent = 0)) }

  @Test
  fun `a bestia with Pack Hunt hits its master's prey harder, and nobody else`() {
    markedByHunter(prey)
    val packBestia = bestiaOf(HUNTER_MASTER_ID, packHuntPercent = 30)

    assertEquals(130, DamageLanding.weigh(world, packBestia, prey, sword, 100))
    assertEquals(100, DamageLanding.weigh(world, packBestia, bystander, sword, 100))
  }

  @Test
  fun `another master's bestia gains nothing from the mark`() {
    markedByHunter(prey)
    val strangerBestia = bestiaOf(OTHER_MASTER_ID, packHuntPercent = 30)

    assertEquals(100, DamageLanding.weigh(world, strangerBestia, prey, sword, 100))
  }

  private fun markedByHunter(target: EntityId) {
    val mark = ActiveStatusEffect(StatusEffectId.HUNTERS_MARK.id, level = 1, remainingSeconds = 30f, sourceEntityId = hunter)
    world.add(target, StatusEffects(mutableListOf(mark)))
  }

  private fun bestiaOf(masterId: Long, packHuntPercent: Int): EntityId {
    return world.createEntity { id ->
      add(id, OwnedBestia(masterId, playerBestiaId = masterId + 100))
      add(id, fighter(packHuntPercent))
    }
  }

  private fun fighter(markedPreyDamagePercent: Int): CombatStats {
    val values = CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1))

    return CombatStats(values.copy(markedPreyDamagePercent = markedPreyDamagePercent))
  }

  private companion object {
    const val HUNTER_MASTER_ID = 7L
    const val OTHER_MASTER_ID = 8L
  }
}

package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TakenDamageTest {

  private val world: EcsWorld = testWorld()

  private fun playerEntity(accountId: Long): EntityId {
    return world.createEntity { id -> add(id, Account(accountId)) }
  }

  @Test
  fun `a master and its bestia count as one account`() {
    val master = playerEntity(1L)
    val bestia = playerEntity(1L)
    val rival = playerEntity(2L)
    val taken = TakenDamage().apply {
      addDamage(master, 30)
      addDamage(bestia, 30)
      addDamage(rival, 50)
    }

    assertEquals(1L, taken.topAccount(world))
  }

  @Test
  fun `a mob's damage counts for nobody`() {
    val mob = world.createEntity { }
    val player = playerEntity(2L)
    val taken = TakenDamage().apply {
      addDamage(mob, 500)
      addDamage(player, 1)
    }

    assertEquals(2L, taken.topAccount(world))
  }

  @Test
  fun `damage from no player names no account`() {
    val mob = world.createEntity { }
    val taken = TakenDamage().apply { addDamage(mob, 500) }

    assertNull(taken.topAccount(world))
  }
}

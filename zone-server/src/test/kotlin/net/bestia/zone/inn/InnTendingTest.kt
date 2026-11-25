package net.bestia.zone.inn

import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.respawn.ecs.Respawn
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InnTendingTest {

  private val world = testWorld()
  private val sut = InnTending()

  /** The examples of the price table on the settlements design page. */
  @Test
  fun `the price follows the level and what is missing`() {
    assertEquals(10, InnTending.priceOf(level = 10, missingHealth = 0.5, missingMana = 0.5, fallen = false))
    assertEquals(70, InnTending.priceOf(level = 10, missingHealth = 1.0, missingMana = 1.0, fallen = true))
    assertEquals(25, InnTending.priceOf(level = 50, missingHealth = 0.25, missingMana = 0.25, fallen = false))
    assertEquals(350, InnTending.priceOf(level = 50, missingHealth = 1.0, missingMana = 1.0, fallen = true))
    assertEquals(693, InnTending.priceOf(level = 99, missingHealth = 1.0, missingMana = 1.0, fallen = true))
  }

  @Test
  fun `only the master's bestias that lack something are quoted`() {
    val hurt = bestia(MASTER, health = 50)
    bestia(MASTER, health = 100)
    bestia(OTHER_MASTER, health = 10)

    val quote = sut.quote(world, MASTER)

    assertEquals(listOf(hurt), quote.map { it.entityId })
    assertEquals(5, quote.single().price)
  }

  @Test
  fun `a standing bestia is restored where it is, a fallen one gets up at the door`() {
    val hurt = bestia(MASTER, health = 50)
    val fallen = bestia(MASTER, health = 0).also { world.add(it, Dead()) }

    sut.tend(world, sut.quote(world, MASTER), DOOR)

    assertEquals(100, world.get(hurt, Health::class)!!.current)
    assertEquals(20, world.get(hurt, Mana::class)!!.current)
    assertEquals(Respawn(DOOR, restored = true), world.get(fallen, Respawn::class))
  }

  private fun bestia(masterId: Long, health: Int): EntityId {
    return world.createEntity { id ->
      add(id, OwnedBestia(masterId = masterId, playerBestiaId = id))
      add(id, Level(10))
      add(id, Health(current = health, max = 100))
      add(id, Mana(current = 20, max = 20))
    }
  }

  private companion object {
    const val MASTER = 1L
    const val OTHER_MASTER = 2L
    val DOOR = Vec3L(4, 5, 6)
  }
}

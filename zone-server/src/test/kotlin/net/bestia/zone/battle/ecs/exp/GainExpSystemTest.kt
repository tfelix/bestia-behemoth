package net.bestia.zone.battle.ecs.exp

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.level.LevelUpExperienceCalculator
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.persistence.EntityWriteBehind
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class GainExpSystemTest {

  private val writeBehind = mockk<EntityWriteBehind>(relaxed = true)
  private val world = testWorld(systems = listOf(GainExpSystem(LevelUpExperienceCalculator(), writeBehind)))

  private fun World.masterGaining(masterId: Long, exp: Int): EntityId {
    return createEntity { id ->
      add(id, Master(masterId, "Tester $masterId"))
      add(id, Exp(0, 1_000))
      add(id, Level(1))
      add(id, GainExp(exp))
    }
  }

  @Test
  fun `every gain is handed to the write-behind, once per tick for all masters`() {
    val first = world.masterGaining(masterId = 1L, exp = 10)
    val second = world.masterGaining(masterId = 2L, exp = 20)

    world.tick(0.05f)

    verify(exactly = 1) { writeBehind.persist(world, listOf(first, second), withStatusEffects = false) }
  }

  @Test
  fun `a tick without a gain writes nothing`() {
    world.tick(0.05f)

    verify(exactly = 0) { writeBehind.persist(any(), any(), any()) }
  }

  @Test
  fun `a gain on a creature that is not a master is not persisted`() {
    world.createEntity { id ->
      add(id, Exp(0, 1_000))
      add(id, Level(1))
      add(id, GainExp(10))
    }

    world.tick(0.05f)

    verify(exactly = 0) { writeBehind.persist(any(), any(), any()) }
  }
}

package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RevivalTest {

  private val world = testWorld()

  @Test
  fun `the dead stand up where they lie with a share of their health`() {
    val body = world.createEntity { id ->
      add(id, Health(0, 200))
      add(id, Dead())
    }

    assertTrue(Revival.revive(world, body, healthPercent = 25))
    assertFalse(world.has(body, Dead::class))
    assertEquals(50, world.get(body, Health::class)!!.current)
  }

  @Test
  fun `the living are not revived`() {
    val living = world.createEntity { id -> add(id, Health(100, 200)) }

    assertFalse(Revival.revive(world, living, healthPercent = 100))
    assertEquals(100, world.get(living, Health::class)!!.current)
  }
}

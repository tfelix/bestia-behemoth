package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryTest {

  private class QPosition(var x: Float = 0f) : Component
  private class QVelocity(var dx: Float = 0f) : Component
  private class QHealth(var value: Int = 100) : Component
  private class QFlag(var tag: Int = 0) : Component

  @Test
  fun `query joins across four component stores`() {
    val world = testWorld()
    val fullEntities = mutableSetOf<EntityId>()

    repeat(200) { i ->
      val e = world.createEntity { }
      world.add(e, QPosition(i.toFloat()))
      if (i % 2 == 0) world.add(e, QVelocity(1f))
      if (i % 3 == 0) world.add(e, QHealth(100))
      if (i % 5 == 0) world.add(e, QFlag(i))

      if (i % 2 == 0 && i % 3 == 0 && i % 5 == 0) fullEntities.add(e)
    }

    val visited = mutableListOf<EntityId>()
    world.query(QPosition::class, QVelocity::class, QHealth::class, QFlag::class).each { id ->
      visited.add(id)
    }

    assertEquals(fullEntities, visited.toSet())
    assertEquals(fullEntities.size, visited.size)
  }

  @Test
  fun `Row get for a type outside the query throws`() {
    val world = testWorld()
    val e = world.createEntity { }
    world.add(e, QPosition(1f))

    val ex = assertThrows(IllegalStateException::class.java) {
      world.query(QPosition::class).each {
        get<QVelocity>()
      }
    }
    assertTrue(ex.message!!.contains("QVelocity"))
  }
}

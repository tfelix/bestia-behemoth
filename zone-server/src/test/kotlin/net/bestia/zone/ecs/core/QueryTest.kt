package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class QueryTest {

  private class QPosition(var x: Float = 0f) : Component
  private class QVelocity(var dx: Float = 0f) : Component
  private class QHealth(var value: Int = 100) : Component
  private class QFlag(var tag: Int = 0) : Component

  @Test
  fun `query joins across four component stores`() {
    val world = World()
    val fullEntities = mutableSetOf<EntityId>()

    repeat(200) { i ->
      val e = world.create()
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
    val world = World()
    val e = world.create()
    world.add(e, QPosition(1f))

    val ex = assertThrows(IllegalStateException::class.java) {
      world.query(QPosition::class).each {
        get<QVelocity>()
      }
    }
    assertTrue(ex.message!!.contains("QVelocity"))
  }

  @Test
  fun `parallelEach gives each entity a consistent row across worker threads`() {
    val world = World()
    val expectedX = ConcurrentHashMap<EntityId, Float>()

    repeat(5000) { i ->
      val e = world.create()
      val x = i.toFloat()
      world.add(e, QPosition(x))
      world.add(e, QVelocity(x * 2))
      expectedX[e] = x
    }

    val visited = Collections.synchronizedList(mutableListOf<EntityId>())
    val mismatches = Collections.synchronizedList(mutableListOf<EntityId>())

    world.query(QPosition::class, QVelocity::class).parallelEach { id ->
      val pos = get<QPosition>()
      val vel = get<QVelocity>()
      visited.add(id)
      if (pos.x != expectedX[id] || vel.dx != pos.x * 2) {
        mismatches.add(id)
      }
    }

    assertEquals(5000, visited.size)
    assertTrue(mismatches.isEmpty())
  }
}

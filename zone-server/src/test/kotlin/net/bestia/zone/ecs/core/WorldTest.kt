package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class Position(var x: Float = 0f, var y: Float = 0f) : Component
private class Velocity(var dx: Float = 0f, var dy: Float = 0f) : Component
private class Health(var value: Int = 100) : Component

class WorldTest {

  @Test
  fun `entities and components lifecycle`() {
    val world = World()
    val e = world.create()
    assertTrue(world.isAlive(e))

    world.add(e, Position(1f, 2f))
    assertTrue(world.has<Position>(e))
    assertEquals(1f, world.get<Position>(e)!!.x)

    world.destroy(e)
    assertFalse(world.isAlive(e))
    assertNull(world.get<Position>(e))
    assertEquals(0, world.entityCount)
  }

  @Test
  fun `query joins on the smaller store`() {
    val world = World()
    // 100 entities with Position, only 3 also have Velocity
    repeat(100) {
      val e = world.create()
      world.add(e, Position(it.toFloat(), 0f))
      if (it < 3) world.add(e, Velocity(1f, 0f))
    }

    val visited = mutableListOf<EntityId>()
    world.query(Position::class, Velocity::class).each { id -> visited.add(id) }

    assertEquals(3, visited.size)
  }

  @Test
  fun `commands are applied at the next tick, not immediately`() {
    val world = World()
    val e = world.create()
    world.add(e, Velocity(0f, 0f))

    world.onCommand<SetVelocity> { w, c -> w.get<Velocity>(c.entity)?.apply { dx = c.dx; dy = c.dy } }

    world.send(SetVelocity(e, 5f, 0f))
    // not drained yet
    assertEquals(0f, world.get<Velocity>(e)!!.dx)

    world.tick(0.1f)
    assertEquals(5f, world.get<Velocity>(e)!!.dx)
  }

  @Test
  fun `component changes can be drained (pull) and observed (push)`() {
    val world = World()
    val e = world.create()
    world.add(e, Position(0f, 0f)) // add marks it changed

    // pull
    val pulled = mutableListOf<EntityId>()
    world.drainChanges<Position> { pulled.add(it) }
    assertEquals(listOf(e), pulled)
    // draining consumed the marks
    val pulledAgain = mutableListOf<EntityId>()
    world.drainChanges<Position> { pulledAgain.add(it) }
    assertTrue(pulledAgain.isEmpty())

    // push
    val observed = mutableListOf<EntityId>()
    world.onChanged<Position> { observed.add(it) }
    world.markChanged<Position>(e)
    world.publishChanges()
    assertEquals(listOf(e), observed)
  }

  @Test
  fun `emitted events are drainable from the outbox`() {
    val world = World()
    world.emit("hello")
    world.emit(42)

    val events = mutableListOf<Any>()
    world.drainOutbox { events.add(it) }
    assertEquals(listOf("hello", 42), events)
  }

  @Test
  fun `structural changes requested inside a system are deferred`() {
    val world = World()
    val e = world.create()
    world.add(e, Health(1))

    // a system that "kills" entities at 0 hp by removing Health mid-iteration
    world.addSystem(object : Ecs2System {
      override val writes = setOf(Health::class)
      override fun update(world: World, deltaTime: Float) {
        world.query(Health::class).each { id ->
          val hp = get<Health>()
          hp.value -= 1
          if (hp.value <= 0) world.remove<Health>(id) // deferred, safe during iteration
        }
      }
    })

    world.tick(0.1f)
    // removal applied at end-of-tick sync point
    assertFalse(world.has<Health>(e))
  }

  private class SetVelocity(val entity: EntityId, val dx: Float, val dy: Float) : Command
}

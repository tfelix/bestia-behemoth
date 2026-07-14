package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

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
    val world = testWorld()
    val e = world.create()
    assertTrue(world.isAlive(e))

    world.add(e, Position(1f, 2f))
    assertTrue(world.has(e, Position::class))
    assertEquals(1f, world.get(e, Position::class)!!.x)

    world.destroy(e)
    assertFalse(world.isAlive(e))
    assertNull(world.get(e, Position::class))
    assertEquals(0, world.entityCount)
  }

  @Test
  fun `query joins on the smaller store`() {
    val world = testWorld()
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
    val world = testWorld()
    val e = world.create()
    world.add(e, Velocity(0f, 0f))

    world.onCommand<SetVelocity> { w, c -> w.get(c.entity, Velocity::class)?.apply { dx = c.dx; dy = c.dy } }

    world.send(SetVelocity(e, 5f, 0f))
    // not drained yet
    assertEquals(0f, world.get(e, Velocity::class)!!.dx)

    world.tick(0.1f)
    assertEquals(5f, world.get(e, Velocity::class)!!.dx)
  }

  @Test
  fun `each visits every stored component of a type`() {
    val world = testWorld()
    val e1 = world.create()
    val e2 = world.create()
    world.add(e1, Position(1f, 2f))
    world.add(e2, Position(3f, 4f))

    val visited = mutableMapOf<EntityId, Float>()
    world.each(Position::class) { id, pos -> visited[id] = pos.x }

    assertEquals(setOf(e1, e2), visited.keys)
    assertEquals(1f, visited[e1])
    assertEquals(3f, visited[e2])
  }

  @Test
  fun `structural changes requested inside a system are deferred`() {
    // a system that "kills" entities at 0 hp by removing Health mid-iteration
    val hpKiller = object : System {
      override val writes = setOf(Health::class)
      override fun update(world: World, deltaTime: Float) {
        world.query(Health::class).each { id ->
          val hp = get<Health>()
          hp.value -= 1
          if (hp.value <= 0) world.remove(id, Health::class) // deferred, safe during iteration
        }
      }
    }
    val world = testWorld(systems = listOf(hpKiller))
    val e = world.create()
    world.add(e, Health(1))

    world.tick(0.1f)
    // removal applied at end-of-tick sync point
    assertFalse(world.has(e, Health::class))
  }

  private class SetVelocity(val entity: EntityId, val dx: Float, val dy: Float) : Command
}

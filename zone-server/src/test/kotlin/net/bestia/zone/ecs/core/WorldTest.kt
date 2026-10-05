package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private class Position(var x: Float = 0f, var y: Float = 0f) : Component
private class Velocity(var dx: Float = 0f, var dy: Float = 0f) : Component
private class Health(var value: Int = 100) : Component

class WorldTest {

  @Test
  fun `entities and components lifecycle`() {
    val world = testWorld()
    val e = world.createEntity { }
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
      val e = world.createEntity { }
      world.add(e, Position(it.toFloat(), 0f))
      if (it < 3) world.add(e, Velocity(1f, 0f))
    }

    val visited = mutableListOf<EntityId>()
    world.query(Position::class, Velocity::class).each { id -> visited.add(id) }

    assertEquals(3, visited.size)
  }

  @Test
  fun `each visits every stored component of a type`() {
    val world = testWorld()
    val e1 = world.createEntity { }
    val e2 = world.createEntity { }
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
      override val phase = Phase.UPKEEP
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
    val e = world.createEntity { }
    world.add(e, Health(1))

    world.tick(0.1f)
    // removal applied at end-of-tick sync point
    assertFalse(world.has(e, Health::class))
  }

  @Test
  fun `deferred changes still apply when a later system throws`() {
    val remover = object : System {
      override val phase = Phase.UPKEEP
      override val writes = setOf(Health::class)
      override fun update(world: World, deltaTime: Float) {
        world.query(Health::class).each { id -> world.remove(id, Health::class) }
      }
    }
    val failing = object : System {
      override val phase = Phase.UPKEEP
      override fun update(world: World, deltaTime: Float) {
        error("boom")
      }
    }
    val world = testWorld(systems = listOf(remover, failing))
    val e = world.createEntity { }
    world.add(e, Health(1))

    world.tick(0.05f)

    assertFalse(world.has(e, Health::class))
  }

  @Test
  fun `one entity that throws inside a tick does not stop the others`() {
    val visited = mutableListOf<EntityId>()
    var bad = -1L
    val system = object : System {
      override val phase = Phase.UPKEEP
      override val reads = setOf(Health::class)

      override fun update(world: World, deltaTime: Float) {
        world.query(Health::class).each { id ->
          if (id == bad) error("bad entity")
          visited.add(id)
        }
      }
    }
    val world = testWorld(systems = listOf(system))
    val first = world.createEntity { }.also { world.add(it, Health()) }
    bad = world.createEntity { }.also { world.add(it, Health()) }
    val last = world.createEntity { }.also { world.add(it, Health()) }

    world.tick(0.05f)

    assertEquals(listOf(first, last), visited)
  }

  @Test
  fun `a failing posted task does not stop the tasks after it`() {
    val world = testWorld()
    val e = world.createEntity { }
    world.add(e, Velocity(0f, 0f))

    world.post { error("broken task") }
    world.post { get(e, Velocity::class)!!.dx = 5f }
    world.tick(0.05f)

    assertEquals(5f, world.get(e, Velocity::class)!!.dx)
  }

  @Test
  fun `an accessor from another thread throws instead of borrowing the world`() {
    val world = testWorld()
    val e = world.createEntity { }
    world.add(e, Health(5))
    val failure = AtomicReference<Throwable>()
    val tick = TickThread(world)

    try {
      thread { failure.set(runCatching { world.get(e, Health::class) }.exceptionOrNull()) }.join(5_000)

      assertTrue(failure.get() is IllegalStateException, "a lone accessor must not take its own lease")
    } finally {
      tick.stop()
    }
  }

  @Test
  fun `a scope from another thread runs on that thread while the tick thread waits`() {
    val world = testWorld()
    val e = world.createEntity { }
    world.add(e, Health(5))
    val ranOn = AtomicReference<Thread>()
    val tick = TickThread(world)

    try {
      val hp = thread { world.modify(e) { id -> ranOn.set(Thread.currentThread()); get(id, Health::class)!!.value } }
      hp.join(5_000)

      assertTrue(ranOn.get() === hp, "the block must run on the borrower's own thread")
    } finally {
      tick.stop()
    }
  }

  @Test
  fun `a scope inside a lent scope runs inline`() {
    val world = testWorld()
    val e = world.createEntity { }
    world.add(e, Health(5))
    val tick = TickThread(world)
    val nested = AtomicReference<Int>()

    try {
      thread { nested.set(world.read { world.read { get(e, Health::class)!!.value } }) }.join(5_000)

      assertEquals(5, nested.get())
    } finally {
      tick.stop()
    }
  }

  @Test
  fun `posted work runs on the next tick`() {
    val world = testWorld()
    var ran = false

    world.post { ran = true }
    assertFalse(ran)

    world.tick(0.05f)
    assertTrue(ran)
  }

  /** Binds a thread as the world's owner and runs posted work on it, the way `ZoneEngine` does. */
  private class TickThread(world: EcsWorld) {
    @Volatile
    private var running = true
    private val bound = CountDownLatch(1)
    private val thread = thread {
      world.bindTickThread()
      bound.countDown()
      while (running) world.runPostedUntil(java.lang.System.nanoTime() + 10_000_000)
      world.unbindTickThread()
    }

    init {
      bound.await()
    }

    fun stop() {
      running = false
      thread.join(5_000)
    }
  }
}

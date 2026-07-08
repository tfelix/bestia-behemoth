package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.spring.Ecs2Configuration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.concurrent.Executors

/**
 * End-to-end scenario proving the whole ecs pipeline through the real Spring
 * wiring: systems are collected as beans, external commands come in on another
 * thread, and component changes + domain events go back out.
 */
class WanderScenarioTest {

  @Configuration
  @ComponentScan(basePackageClasses = [WanderSystem::class])
  @Import(Ecs2Configuration::class)
  class ScenarioConfig

  private lateinit var ctx: AnnotationConfigApplicationContext
  private lateinit var world: World

  @BeforeEach
  fun setUp() {
    ctx = AnnotationConfigApplicationContext(ScenarioConfig::class.java)
    world = ctx.getBean(World::class.java)
    // Register the command handler that turns external intent into ECS state.
    world.onCommand<MoveCommand> { w, cmd ->
      w.get<Velocity>(cmd.entity)?.apply { dx = cmd.dx; dy = cmd.dy }
    }
  }

  @AfterEach
  fun tearDown() {
    ctx.close()
  }

  private fun spawnCritter(): EntityId {
    val e = world.create()
    world.add(e, Position(0f, 0f))
    world.add(e, Velocity(0f, 0f))
    world.add(e, Wander())
    world.add(e, Health(50))
    return e
  }

  @Test
  fun `spring collects the systems and schedules them into waves`() {
    assertEquals(3, world.systemCount)
    // MovementSystem (reads Velocity) conflicts with WanderSystem (writes Velocity)
    // => 2 waves; HealthRegenSystem is independent and shares the first wave.
    assertEquals(2, world.waveCount)
  }

  @Test
  fun `wandering critters move and their changes and events flow outward`() {
    val critters = (1..10).map { spawnCritter() }
    // drain the "component added" marks from spawn so we only observe movement
    world.drainChanges<Position> { }

    repeat(20) { world.tick(0.05f) }

    // pull model: which positions changed
    val movedIds = mutableSetOf<EntityId>()
    world.drainChanges<Position> { movedIds.add(it) }
    assertTrue(movedIds.isNotEmpty(), "wandering critters should have moved")
    assertTrue(critters.containsAll(movedIds))

    // outbox: discrete movement events were emitted
    var eventCount = 0
    world.drainOutbox { event ->
      assertTrue(event is EntityMoved)
      eventCount++
    }
    assertTrue(eventCount > 0, "expected EntityMoved events in the outbox")
  }

  @Test
  fun `an external thread steers an entity via a command applied on the next tick`() {
    // a plain "player" entity: only the command drives it (no Wander overwrites velocity)
    val player = world.create()
    world.add(player, Position(0f, 0f))
    world.add(player, Velocity(0f, 0f))

    // send from a different thread, like the network layer would
    val net = Executors.newSingleThreadExecutor()
    net.submit { world.send(MoveCommand(player, dx = 10f, dy = 0f)) }.get()
    net.shutdown()

    // not applied until the tick drains the queue
    assertEquals(0f, world.get<Velocity>(player)!!.dx)

    world.tick(0.1f)

    assertEquals(10f, world.get<Velocity>(player)!!.dx)
    assertTrue(world.get<Position>(player)!!.x > 0f, "player should have moved after the command")
  }

  @Test
  fun `push model fans component changes out to a registered observer`() {
    val observed = mutableListOf<EntityId>()
    world.onChanged<Health> { observed.add(it) }

    val e = spawnCritter()
    world.drainChanges<Health> { } // clear spawn marks

    // regen fires every 0.1s; 5 ticks of 0.05s = 0.25s -> at least two regen passes
    repeat(5) { world.tick(0.05f) }
    world.publishChanges()

    assertTrue(observed.contains(e), "health regen should have marked the critter changed")
    assertFalse(world.isAlive(-1L))
  }
}

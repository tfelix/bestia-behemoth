package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.EcsConfiguration
import net.bestia.zone.ZoneConfig as ZoneShardConfig
import net.bestia.zone.ecs.ZoneConfig as WorldConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.concurrent.Executors
import net.bestia.zone.ecs.core.EcsWorld

/**
 * End-to-end scenario proving the whole ecs pipeline through the real Spring
 * wiring: systems are collected as beans, external commands come in on another
 * thread, and component changes go back out (pull model).
 */
class WanderScenarioTest {

  @Configuration
  @ComponentScan(basePackageClasses = [WanderSystem::class])
  @Import(EcsConfiguration::class)
  class ScenarioConfig {

    // ecsWorld() takes these as typed config beans rather than @Value properties; this minimal
    // context has no property source to bind them from, so supply plain test instances directly.
    @Bean
    fun worldConfig(): WorldConfig = WorldConfig(tickRate = 20, parallelSystems = false)

    @Bean
    fun zoneShardConfig(): ZoneShardConfig = ZoneShardConfig(
      bestiaBaseSlotCount = 4,
      bestiaMaxSlotCount = 4,
      jwtAuthSecretKey = "test-secret",
      shardId = 1,
    )
  }

  private lateinit var ctx: AnnotationConfigApplicationContext
  private lateinit var world: EcsWorld

  @BeforeEach
  fun setUp() {
    ctx = AnnotationConfigApplicationContext(ScenarioConfig::class.java)
    world = ctx.getBean(EcsWorld::class.java)
  }

  @AfterEach
  fun tearDown() {
    ctx.close()
  }

  private fun spawnCritter(): EntityId {
    val e = world.createEntity { }
    world.add(e, Position(0f, 0f))
    world.add(e, Velocity(0f, 0f))
    world.add(e, Wander())
    world.add(e, Health(50))
    return e
  }

  @Test
  fun `spring collects the systems and schedules them into waves`() {
    assertEquals(3, world.systemCount)
    // One phase each (AI, movement, recovery), and phases never share a wave.
    assertEquals(3, world.waveCount)
  }

  @Test
  fun `wandering critters actually move over several ticks`() {
    val critters = (1..10).map { spawnCritter() }
    val startPositions = critters.associateWith {
      val p = world.get(it, Position::class)!!
      p.x to p.y
    }

    // Not a multiple of four: a critter's direction cycles every four steps, which brings it back home.
    repeat(5) { world.tick(0.05f) }

    val movedIds = critters.filter { id ->
      val p = world.get(id, Position::class)!!
      (p.x to p.y) != startPositions[id]
    }
    assertTrue(movedIds.isNotEmpty(), "wandering critters should have moved")
  }

  @Test
  fun `an external thread steers an entity via work posted for the next tick`() {
    // a plain "player" entity: only the posted work drives it (no Wander overwrites velocity)
    val player = world.createEntity { }
    world.add(player, Position(0f, 0f))
    world.add(player, Velocity(0f, 0f))

    // send from a different thread, like the network layer would
    val net = Executors.newSingleThreadExecutor()
    net.submit { world.post { get(player, Velocity::class)?.dx = 10f } }.get()
    net.shutdown()

    // not applied until the tick runs the posted work
    assertEquals(0f, world.get(player, Velocity::class)!!.dx)

    world.tick(0.1f)

    assertEquals(10f, world.get(player, Velocity::class)!!.dx)
    assertTrue(world.get(player, Position::class)!!.x > 0f, "player should have moved after the posted work")
  }

  @Test
  fun `health regen increases a damaged critter's HP over several ticks`() {
    val e = spawnCritter() // spawns with Health(50), max 100
    val before = world.get(e, Health::class)!!.value

    // regen fires every 0.1s; 5 ticks of 0.05s = 0.25s -> at least two regen passes
    repeat(5) { world.tick(0.05f) }

    val after = world.get(e, Health::class)!!.value
    assertTrue(after > before, "health regen should have increased the critter's HP")
    assertFalse(world.isAlive(-1L))
  }
}

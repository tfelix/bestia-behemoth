package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Who may think less often, and - much more importantly - who may not.
 *
 * The second half is the point of the whole design. A rule based on distance alone would throttle a player's
 * bestia sent off to do something out of sight, and a boss standing in its lair, both of which have to keep
 * acting at full speed. So the marker is the gate and these tests pin that: everything without it comes back
 * at factor 1 whatever its position.
 */
class AiThrottleTest {

  private val config = AmbientSpawnConfig()
  private val sut = AiThrottle(config)

  private val nearbyPlayer = listOf(Vec3L(0, 0, 0))
  private val distantPlayer = listOf(Vec3L(5_000, 5_000, 0))

  @Test
  fun `an ambient creature far from every player is throttled`() {
    val world = testWorld()
    val id = agent(world, at = Vec3L(1_000, 1_000, 0), throttleable = true)

    assertEquals(config.throttleFactor, factorFor(world, id, distantPlayer))
  }

  @Test
  fun `an ambient creature close to a player runs at full rate`() {
    val world = testWorld()
    val id = agent(world, at = Vec3L(10, 10, 0), throttleable = true)

    assertEquals(1, factorFor(world, id, nearbyPlayer))
  }

  /** A den mob, a boss and a `/spawn`ed creature all reach here: no marker, no throttling. */
  @Test
  fun `a creature without the marker is never throttled`() {
    val world = testWorld()
    val id = agent(world, at = Vec3L(9_000, 9_000, 0), throttleable = false)

    assertEquals(1, factorFor(world, id, distantPlayer))
  }

  @Test
  fun `a player-controlled bestia working at a distance is never throttled`() {
    val world = testWorld()
    val id = agent(world, at = Vec3L(9_000, 9_000, 0), throttleable = true)
    world.add(id, PlayerControlled)
    world.tick(0.05f)

    assertEquals(1, factorFor(world, id, distantPlayer))
  }

  /** A fight that wandered out of sight must not go into slow motion. */
  @Test
  fun `an angry ambient creature is never throttled`() {
    val world = testWorld()
    val memory = Blackboard()
    memory.set(BestiaDomain.IS_AGGRO, true)
    val id = agent(world, at = Vec3L(9_000, 9_000, 0), throttleable = true, memory = memory)

    assertEquals(1, factorFor(world, id, distantPlayer))
  }

  @Test
  fun `a throttle factor of one disables the mechanism`() {
    val off = AiThrottle(AmbientSpawnConfig(throttleFactor = 1))
    val world = testWorld()
    val id = agent(world, at = Vec3L(9_000, 9_000, 0), throttleable = true)

    assertEquals(1, off.factorFor(world, id, world.getOrThrow(id, AiAgent::class), distantPlayer))
    assertEquals(false, off.isActive)
  }

  private fun factorFor(world: World, id: EntityId, players: List<Vec3L>): Int {
    return sut.factorFor(world, id, world.getOrThrow(id, AiAgent::class), players)
  }

  private fun agent(
    world: World,
    at: Vec3L,
    throttleable: Boolean,
    memory: Blackboard = Blackboard()
  ): EntityId {
    val agent = mockk<AiAgent>(relaxed = true)
    every { agent.memory } returns memory

    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, agent)
      if (throttleable) add(id, AiThrottleable)
    }
  }
}

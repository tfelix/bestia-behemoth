package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Who gets less processing, and - much more importantly - who may not.
 *
 * The second half is the point of the whole design. A rule based on distance alone would slow a player's
 * bestia sent off to do something out of sight, and a fight that wandered out of view, both of which have to
 * keep acting at full speed.
 */
class AiThrottleTest {

  private val config = AmbientSpawnConfig()
  private val players = ActivePlayerAOIService()
  private val observers = mutableMapOf<EntityId, Set<AccountId>>()
  private val visibility = mockk<EntityVisibility>().also {
    every { it.observersOf(any()) } answers { observers[firstArg()] ?: emptySet() }
  }
  private val sut = AiThrottle(config, players, visibility)

  private val world = testWorld()

  init {
    players.setEntityPosition(PLAYER, Vec3L(0, 0, 0))
  }

  @Test
  fun `an ambient creature near a player gets full detail`() {
    val id = agent(at = Vec3L(10, 10, 0), throttleable = true)

    assertEquals(AiDetail.FULL, detailOf(id))
  }

  @Test
  fun `an ambient creature someone can see, but far from players, gets reduced detail`() {
    val id = agent(at = Vec3L(1_000, 1_000, 0), throttleable = true)
    observers[id] = setOf(ACCOUNT)

    assertEquals(AiDetail.REDUCED, detailOf(id))
  }

  @Test
  fun `an ambient creature nobody can see runs in the background, not frozen`() {
    val id = agent(at = Vec3L(1_000, 1_000, 0), throttleable = true)

    assertEquals(AiDetail.BACKGROUND, detailOf(id))
  }

  /** A den mob and a `/spawn`ed creature reach here: no marker, so never the background tier. */
  @Test
  fun `a creature without the marker never drops below reduced`() {
    val id = agent(at = Vec3L(9_000, 9_000, 0), throttleable = false)

    assertEquals(AiDetail.REDUCED, detailOf(id))
  }

  @Test
  fun `a profile floor keeps an unseen creature at full detail`() {
    val id = agent(at = Vec3L(9_000, 9_000, 0), throttleable = true, minDetail = AiDetail.FULL)

    assertEquals(AiDetail.FULL, detailOf(id))
  }

  @Test
  fun `a player-controlled bestia working at a distance gets full detail`() {
    val id = agent(at = Vec3L(9_000, 9_000, 0), throttleable = true)
    world.add(id, PlayerControlled)

    assertEquals(AiDetail.FULL, detailOf(id))
  }

  /** A fight that wandered out of sight must not go into slow motion. */
  @Test
  fun `an angry creature gets full detail`() {
    val memory = Blackboard()
    memory.set(BestiaDomain.IS_AGGRO, true)
    val id = agent(at = Vec3L(9_000, 9_000, 0), throttleable = true, memory = memory)

    assertEquals(AiDetail.FULL, detailOf(id))
  }

  @Test
  fun `each tier runs its configured factor slower`() {
    val agent = mockk<AiAgent>()

    every { agent.detail } returns AiDetail.FULL
    assertEquals(1, sut.factorOf(agent))

    every { agent.detail } returns AiDetail.REDUCED
    assertEquals(config.throttleFactor, sut.factorOf(agent))

    every { agent.detail } returns AiDetail.BACKGROUND
    assertEquals(config.backgroundFactor, sut.factorOf(agent))
  }

  private fun detailOf(id: EntityId): AiDetail {
    return sut.detailOf(world, id, world.getOrThrow(id, AiAgent::class))
  }

  private fun agent(
    at: Vec3L,
    throttleable: Boolean,
    memory: Blackboard = Blackboard(),
    minDetail: AiDetail = AiDetail.BACKGROUND,
  ): EntityId {
    val agent = mockk<AiAgent>(relaxed = true)
    every { agent.memory } returns memory
    every { agent.minDetail } returns minDetail

    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, agent)
      if (throttleable) add(id, AiThrottleable)
    }
  }

  private companion object {
    const val PLAYER = 1_000_000L
    const val ACCOUNT = 7L
  }
}

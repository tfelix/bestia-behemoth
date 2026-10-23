package net.bestia.zone.ai.bt.leaves

import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.behavior.Status
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyEndedEvent
import net.bestia.zone.travel.TravelConfig
import net.bestia.zone.travel.TravelTestWorld
import net.bestia.zone.travel.knowledge.RouteKnowledge
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TravelToTest {

  private val config = TravelConfig()
  private val ended = mutableListOf<JourneyEndedEvent>()
  private val travel = TravelTestWorld.onOpenGround(config, RouteKnowledge(config), ended::add)
  private val creature = travel.bestiaAt(masterId = 1L, position = Vec3L(0, 0, 0))

  private fun contextFor(id: EntityId): BtContext {
    return BtContext(
      world = travel.world,
      entityId = id,
      memory = Blackboard(),
      state = WorldState.EMPTY,
      deltaTime = TravelTestWorld.DT,
      currentTick = travel.world.tickCount,
      tickRate = 20,
    )
  }

  @Test
  fun `travels however far and succeeds on arrival`() {
    val leaf = TravelTo(Vec3L(200, 0, 0), travel.journeys)
    var status = Status.RUNNING

    repeat(20 * 120) {
      if (status != Status.RUNNING) return@repeat
      status = leaf.tick(contextFor(creature))
      travel.tick()
    }

    assertEquals(Status.SUCCESS, status)
    assertTrue(Math.abs(travel.positionOf(creature).x - 200) <= 1, "arrived within a tile")
  }

  @Test
  fun `a journey its AI stops tending lapses without a word`() {
    val leaf = TravelTo(Vec3L(200, 0, 0), travel.journeys)
    repeat(3) {
      leaf.tick(contextFor(creature))
      travel.tick()
    }
    assertTrue(travel.world.has(creature, Journey::class))

    repeat(20 * 6) { travel.tick() }

    assertFalse(travel.world.has(creature, Journey::class))
    assertTrue(ended.isEmpty(), "a lapsed journey is nobody's news")
  }
}

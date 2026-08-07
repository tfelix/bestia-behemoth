package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.VegetationMemory
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * End-to-end demonstration of the whole AI stack driving one mob through its full behavioural lifecycle:
 * idling until restless, spotting a player, chasing, attacking, and finally fleeing at low health.
 *
 * Unlike [AiBehaviorScenarioTest] (single transitions), this runs a faithful mini game-loop: the real AI
 * systems plus the real `MoveSystem` registered in an ecs world and stepped at 20 tps, so perception, drives,
 * planning, behaviour trees and movement all interleave the way the live engine loop makes them.
 */
class AiLifecycleE2ETest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  @Test
  fun `a mob idles, hunts the player, closes to melee, then flees when hurt`() {
    // ---- Phase 1: a lone mob, nobody around. Restlessness is the only drive that will fire first. ----
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0), health = 10, maxHealth = 10)

    ai.tickUntilGoal(mob, "Wander")

    // ---- Phase 2: a player appears within sight. ----
    val player = ai.spawnPlayer(Vec3L(6, 0, 0), health = 30)

    ai.tickUntilGoal(mob, "KillEnemy")
    ai.tickUntil(describe = { "the mob never closed to melee (d=${ai.distanceBetween(mob, player)})" }) {
      ai.distanceBetween(mob, player) <= 1
    }

    // ---- Phase 3: badly wounded. ----
    ai.setHealth(mob, 2) // 20% of max, below the profile's 35% flee threshold
    ai.tickUntilGoal(mob, "Flee")

    val distanceWhenHurt = ai.distanceBetween(mob, player)
    ai.tickUntil(describe = { "the fleeing mob never increased its distance from $distanceWhenHurt" }) {
      ai.distanceBetween(mob, player) > distanceWhenHurt
    }
  }

  @Test
  fun `a hungry mob walks to a remembered vegetation spot and only then believes it has eaten`() {
    val mob = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0))
    val agent = ai.agentOf(mob)
    val spot = Vec3L(6, 0, 0)

    agent.memory.set(BestiaDomain.HUNGER, 95)
    agent.memory.set(BestiaDomain.KNOWN_VEGETATION, listOf(VegetationMemory(spot, discoveredAtMs = 0L)))

    ai.tickUntilGoal(mob, "EatVegetation")

    // Deciding to eat is not eating. This is the whole point of applying effects on observed success: mid-walk
    // the mob is still hungry and still knows about the spot.
    assertTrue((agent.memory.get(BestiaDomain.HUNGER) ?: 0) > 15, "not fed merely by having decided to eat")

    ai.tickUntil(describe = { "hunger was never spent (h=${agent.memory.get(BestiaDomain.HUNGER)})" }) {
      (agent.memory.get(BestiaDomain.HUNGER) ?: 100) <= 15
    }
    assertTrue(
      agent.memory.get(BestiaDomain.KNOWN_VEGETATION).orEmpty().isEmpty(),
      "the grazed-out spot should be forgotten",
    )
  }

  @Test
  fun `a mob walking somewhere never believes it arrived before it did`() {
    val mob = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0))
    val agent = ai.agentOf(mob)
    val spot = Vec3L(30, 0, 0)

    agent.memory.set(BestiaDomain.HUNGER, 95)
    agent.memory.set(BestiaDomain.KNOWN_VEGETATION, listOf(VegetationMemory(spot, discoveredAtMs = 0L)))

    ai.tickUntilGoal(mob, "EatVegetation")

    // Position is an observation, so only perception writes it. The old plan executor wrote every touched key
    // back at plan time, which meant deciding to walk somewhere teleported the agent's belief about itself.
    val believed = agent.memory.get(BestiaDomain.POSITION)
    assertEquals(ai.positionOf(mob), believed, "believed position must track the real one, not the plan")
    assertTrue(believed!!.distance(spot) > 1, "and it certainly should not be at a spot 30 tiles away yet")
  }
}

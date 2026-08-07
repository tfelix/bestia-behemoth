package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.IdleStance
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The player-facing half of the AI: what an owned bestia does while nobody is driving it, and the guarantees
 * around what a player is and is not allowed to change.
 */
class PlayerIdleAiTest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun agentWith(profileId: String, config: AiConfig?) =
    ai.agentFactory.create(ai.profiles.getOrThrow(profileId), homePosition = Vec3L.ZERO, config = config)

  // ------------------------------------------------------------- stance narrows

  @Test
  fun `a stance narrows the archetype's goals to the ones it permits`() {
    val patrolling = agentWith("aggressive_melee", AiConfig(stance = IdleStance.PATROL))
    val names = patrolling.goals.map { it.name }

    assertTrue(names.containsAll(listOf("Wander", "ReturnHome", "KillAttacker", "Flee")))
    assertFalse(names.contains("KillEnemy"), "a patrolling bestia must not go hunting")
    assertFalse(names.contains("EatVegetation"), "patrol is not forage")
  }

  @Test
  fun `holding leaves only self defence`() {
    val holding = agentWith("aggressive_melee", AiConfig(stance = IdleStance.HOLD))

    assertEquals(setOf("KillAttacker", "Flee"), holding.goals.map { it.name }.toSet())
  }

  @Test
  fun `defend enables hunting for an archetype that can hunt`() {
    val defending = agentWith("aggressive_melee", AiConfig(stance = IdleStance.DEFEND))

    assertTrue(defending.goals.any { it.name == "KillEnemy" })
  }

  @Test
  fun `a stance cannot grant a behaviour the species does not have`() {
    // The critter's profile has no KillEnemy goal at all, so telling it to DEFEND cannot make it a hunter. The
    // stance intersects the archetype's goals rather than replacing them precisely so this is impossible.
    val critter = agentWith("passive_wanderer", AiConfig(stance = IdleStance.DEFEND))

    assertFalse(critter.goals.any { it.name == "KillEnemy" })
    assertTrue(critter.goals.any { it.name == "KillAttacker" }, "but it still defends itself")
  }

  @Test
  fun `every stance keeps self defence`() {
    IdleStance.entries.forEach { stance ->
      val agent = agentWith("aggressive_melee", AiConfig(stance = stance))
      assertTrue(
        agent.goals.any { it.name == "KillAttacker" } && agent.goals.any { it.name == "Flee" },
        "stance $stance must not be able to switch off self defence",
      )
    }
  }

  @Test
  fun `a wild mob with no config runs its archetype unchanged`() {
    val wild = agentWith("aggressive_melee", config = null)

    assertTrue(wild.goals.any { it.name == "KillEnemy" })
    assertTrue(wild.goals.any { it.name == "EatVegetation" })
  }

  // ------------------------------------------------------------------ clamping

  @Test
  fun `player supplied numbers are clamped rather than trusted`() {
    val absurd = AiConfig(stance = IdleStance.DEFEND, aggression = 5_000, fleeThresholdPct = -20).sanitised()

    assertEquals(100, absurd.aggression)
    assertEquals(0, absurd.fleeThresholdPct)
  }

  @Test
  fun `clamped knobs reach the agent's memory and override the archetype's`() {
    val agent = agentWith("aggressive_melee", AiConfig(stance = IdleStance.DEFEND, aggression = 999, fleeThresholdPct = 70))

    assertEquals(100, agent.memory.get(BestiaDomain.AGGRESSION))
    assertEquals(70, agent.memory.get(BestiaDomain.FLEE_THRESHOLD_PCT))
    // The archetype's own value was 35; the player's overrides it.
    assertNotEquals(35, agent.memory.get(BestiaDomain.FLEE_THRESHOLD_PCT))
  }

  @Test
  fun `an unspecified stance has a sensible default rather than nothing`() {
    // Rows written before the column existed read back as this, so it has to be a stance that behaves well.
    assertEquals(IdleStance.PATROL, AiConfig().stance)
  }

  // -------------------------------------------------------- player-controlled

  @Test
  fun `the entity a player is driving does not think or act for itself`() {
    val bestia = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    ai.spawnPlayer(Vec3L(3, 0, 0))

    ai.world.add(bestia, PlayerControlled)
    ai.tick(times = 60)

    assertEquals(null, ai.goalNameOf(bestia), "a player-driven creature must not be planning behind their back")
  }

  @Test
  fun `perception keeps running for a controlled entity so it is not blind when handed back`() {
    val bestia = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    val player = ai.spawnPlayer(Vec3L(3, 0, 0))

    ai.world.add(bestia, PlayerControlled)
    ai.tick(times = 30)

    // Deliberately still observing: the moment control is released the creature must act on the world as it is,
    // not on whatever was true when the player took over.
    assertEquals(player, ai.agentOf(bestia).memory.get(BestiaDomain.TARGET_ID))
  }

  @Test
  fun `releasing control lets the creature resume its own decisions`() {
    val bestia = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    ai.spawnPlayer(Vec3L(3, 0, 0))

    ai.world.add(bestia, PlayerControlled)
    ai.tick(times = 30)
    assertEquals(null, ai.goalNameOf(bestia))

    ai.world.remove(bestia, PlayerControlled::class)
    ai.tickUntilGoal(bestia, "KillEnemy")
  }
}

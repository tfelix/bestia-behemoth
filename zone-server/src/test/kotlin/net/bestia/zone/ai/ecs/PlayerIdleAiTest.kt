package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.WorkArea
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.bestia.DefaultAttack
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
    ai.agentFactory.create(ai.profiles.getOrThrow(profileId), DefaultAttack.MELEE, AttackSpeed.DEFAULT_SPECIES_ASPD, homePosition = Vec3L.ZERO, config = config)

  // --------------------------------------------------------------- job narrows

  @Test
  fun `a job narrows the archetype's goals to the ones it permits`() {
    val resting = agentWith("aggressive_melee", AiConfig(job = BestiaJob.REST))
    val names = resting.goals.map { it.name }

    assertTrue(names.containsAll(listOf("Wander", "ReturnHome", "KillAttacker", "EatVegetation", "Sleep")))
    assertFalse(names.contains("KillEnemy"), "a resting bestia must not go hunting")
  }

  @Test
  fun `guarding enables hunting for an archetype that can hunt`() {
    val guarding = agentWith("aggressive_melee", AiConfig(job = BestiaJob.GUARD))
    val names = guarding.goals.map { it.name }

    assertTrue(names.contains("KillEnemy"))
    assertFalse(names.contains("Sleep"), "a guard stays awake")
  }

  @Test
  fun `a job cannot grant a behaviour the species does not have`() {
    // The critter's profile has no KillEnemy goal at all, so telling it to GUARD cannot make it a hunter. The
    // job intersects the archetype's goals rather than replacing them precisely so this is impossible.
    val critter = agentWith("passive_wanderer", AiConfig(job = BestiaJob.GUARD))

    assertFalse(critter.goals.any { it.name == "KillEnemy" })
    assertTrue(critter.goals.any { it.name == "KillAttacker" }, "but it still defends itself")
  }

  @Test
  fun `every job keeps self defence`() {
    BestiaJob.entries.forEach { job ->
      val agent = agentWith("aggressive_melee", AiConfig(job = job))
      assertTrue(
        agent.goals.any { it.name == "KillAttacker" },
        "job $job must not be able to switch off self defence",
      )
    }
  }

  @Test
  fun `every job adds self care, whatever the species`() {
    BestiaJob.entries.forEach { job ->
      val names = agentWith("passive_wanderer", AiConfig(job = job)).goals.map { it.name }

      assertTrue(names.containsAll(AiConfig.SELF_CARE_GOALS), "job $job lacks self care: $names")
    }
  }

  @Test
  fun `a wild mob with no config runs its archetype unchanged`() {
    val wild = agentWith("aggressive_melee", config = null)

    assertTrue(wild.goals.any { it.name == "KillEnemy" })
    assertTrue(wild.goals.any { it.name == "EatVegetation" })
    assertTrue(wild.goals.none { it.name in AiConfig.SELF_CARE_GOALS }, "a wild mob that bolted could not be fought")
  }

  // ------------------------------------------------------------------- courage

  @Test
  fun `the courage step reaches the agent's memory and overrides the archetype's aggression`() {
    val agent = agentWith("aggressive_melee", AiConfig(job = BestiaJob.GUARD, courage = Courage.TIMID))

    // The archetype's own value was 80; the player's step overrides it.
    assertEquals(Courage.TIMID.aggression, agent.memory.get(BestiaDomain.AGGRESSION))
    assertNotEquals(80, agent.memory.get(BestiaDomain.AGGRESSION))
  }

  @Test
  fun `courage steps only ever grow bolder`() {
    val aggressions = Courage.entries.map { it.aggression }
    val retreatLines = Courage.entries.map { it.retreatBelowHealthPct ?: 0 }
    val takesOnStrongerFoes = Courage.entries.map { it.takesOnStrongerFoes }

    assertEquals(aggressions.sorted(), aggressions)
    assertTrue(aggressions.all { it in 0..100 })
    assertEquals(retreatLines.sortedDescending(), retreatLines)
    assertEquals(takesOnStrongerFoes.sorted(), takesOnStrongerFoes)
  }

  @Test
  fun `the retreat line reaches the agent's memory, and a brave bestia has none`() {
    val careful = agentWith("aggressive_melee", AiConfig(courage = Courage.CAREFUL))
    val brave = agentWith("aggressive_melee", AiConfig(courage = Courage.BRAVE))

    assertEquals(50, careful.memory.get(BestiaDomain.RETREAT_BELOW_HEALTH_PCT))
    assertEquals(null, brave.memory.get(BestiaDomain.RETREAT_BELOW_HEALTH_PCT))
  }

  @Test
  fun `a guard holds the area around its home`() {
    val guard = agentWith("aggressive_melee", AiConfig(job = BestiaJob.GUARD))

    assertEquals(WorkArea(Vec3L.ZERO, radius = 24), guard.memory.get(BestiaDomain.GUARD_AREA))
    assertTrue(guard.goals.any { it.name == "ReturnToMiddle" })
  }

  @Test
  fun `unset orders have a sensible default rather than nothing`() {
    // Rows written before the columns existed read back as this, so it has to be orders that behave well.
    assertEquals(AiConfig(BestiaJob.REST, Courage.STEADY), AiConfig())
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

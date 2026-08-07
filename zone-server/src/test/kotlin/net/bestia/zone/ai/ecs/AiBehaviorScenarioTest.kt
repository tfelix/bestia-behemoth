package net.bestia.zone.ai.ecs

import io.mockk.verify
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * In-process exercise of the merged AI pipeline against a real ecs world: spawn a mob and a player, run the
 * loop, and assert the mob perceives, plans, moves and attacks — plus the transitions between goals.
 *
 * These are single-transition checks; [AiLifecycleE2ETest] runs a whole behavioural lifecycle.
 *
 * Note the deliberate absence of fixed tick counts. Perception refreshes twice a second and each agent thinks
 * on its own staggered period, so nothing is decided in the first few ticks and the exact tick a decision
 * lands on is not part of the contract. `tickUntil` asserts the outcome without pinning the timing.
 */
class AiBehaviorScenarioTest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  @Test
  fun `perception records the player as a target and the mob plans to close and attack`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    val player = ai.spawnPlayer(Vec3L(4, 0, 0))

    ai.tickUntilGoal(mob, "KillEnemy")

    val agent = ai.agentOf(mob)
    assertEquals(player, agent.memory.get(BestiaDomain.TARGET_ID))
    assertEquals(true, agent.memory.get(BestiaDomain.ENEMY_IN_SIGHT))
    assertEquals(
      listOf("approachTarget", "attack(claw)"),
      agent.currentPlan?.actions?.map { it.name },
    )
  }

  @Test
  fun `the mob paths toward the player it decided to attack`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    ai.spawnPlayer(Vec3L(4, 0, 0))

    ai.tickUntil(describe = { "the mob never started a path" }) {
      ai.world.get(mob, Path::class)?.path?.isNotEmpty() == true
    }

    val path = ai.world.get(mob, Path::class)!!.path
    assertNotNull(path)
    assertTrue(path.first().x > 0, "the mob should step toward the player on +x")
  }

  @Test
  fun `the mob casts its attack through the skill service once in melee range`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    val player = ai.spawnPlayer(Vec3L(1, 0, 0))

    ai.tickUntilGoal(mob, "KillEnemy")
    ai.tick(times = 20)

    // Going through the skill service rather than stacking a Damage component directly is the point: a mob
    // attacks by the same route a player does, so range, mana and the strategy script all apply.
    verify(atLeast = 1) {
      ai.skills.execute(
        world = ai.world,
        casterId = mob,
        skillId = 0L,
        skillLevel = 1,
        targetEntityId = player,
        targetPosition = null,
      )
    }
  }

  @Test
  fun `a wounded mob switches from hunting to fleeing and retreats`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0), health = 10, maxHealth = 10)
    val player = ai.spawnPlayer(Vec3L(2, 0, 0))

    ai.tickUntilGoal(mob, "KillEnemy")

    // 20% of max, under the profile's 35% flee threshold.
    ai.setHealth(mob, 2)
    ai.tickUntilGoal(mob, "Flee")

    val distanceBefore = ai.distanceBetween(mob, player)
    ai.tickUntil(describe = { "the fleeing mob never opened the distance from $distanceBefore" }) {
      ai.distanceBetween(mob, player) > distanceBefore
    }
  }

  @Test
  fun `a peaceful critter ignores a player until it is actually hit`() {
    val critter = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0), health = 10, maxHealth = 10)
    val player = ai.spawnPlayer(Vec3L(2, 0, 0))

    // Wait until perception has definitely looked at the world at least once.
    ai.tickUntil(describe = { "perception never ran" }) {
      ai.agentOf(critter).memory.get(BestiaDomain.POSITION) != null
    }

    // The critter has no KillEnemy goal at all, so seeing a player is no reason to attack one.
    assertNotEquals("KillEnemy", ai.goalNameOf(critter), "a passive archetype must not pick a fight")
    assertEquals(false, ai.agentOf(critter).memory.get(BestiaDomain.IS_AGGRO))

    // Being attacked, though, is not something a profile opts out of.
    ai.recordHit(victim = critter, attacker = player)
    ai.tickUntil(describe = { "the critter never noticed it was being attacked" }) {
      ai.agentOf(critter).memory.get(BestiaDomain.IS_AGGRO) == true
    }

    assertEquals(player, ai.agentOf(critter).memory.get(BestiaDomain.TARGET_ID))
  }

  @Test
  fun `retaliation targets whoever actually hit the mob, not merely whoever is nearest`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))
    val nearby = ai.spawnPlayer(Vec3L(1, 0, 0))
    val attackerFurtherAway = ai.spawnPlayer(Vec3L(5, 0, 0))

    ai.recordHit(victim = mob, attacker = attackerFurtherAway)
    ai.tickUntil(describe = { "perception never picked a target" }) {
      ai.agentOf(mob).memory.get(BestiaDomain.TARGET_ID) != null
    }

    val target = ai.agentOf(mob).memory.get(BestiaDomain.TARGET_ID)
    assertEquals(attackerFurtherAway, target, "whoever is hitting us outranks whoever is closest")
    assertNotEquals(nearby, target)
  }

  @Test
  fun `a brand new agent does not immediately decide to go home`() {
    val mob = ai.spawnMob("aggressive_melee", Vec3L(0, 0, 0))

    // Its memory is empty until perception fills it in, and an unknown position must not read as "infinitely
    // far from home" — that made the first decision of every creature's life a pointless trip home.
    ai.tick(times = 3)
    assertNotEquals("ReturnHome", ai.goalNameOf(mob))
  }

  @Test
  fun `an idle mob eventually gets restless and wanders`() {
    val mob = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0))

    // Nothing to see, nothing pressing: the drive system raises restlessness until wandering becomes an
    // available, genuinely unsatisfied goal. This is what replaced the old reflexive fallback.
    ai.tickUntilGoal(mob, "Wander")

    assertTrue(
      (ai.agentOf(mob).memory.get(BestiaDomain.RESTLESSNESS) ?: 0) > 0,
      "restlessness should have accumulated",
    )
  }
}

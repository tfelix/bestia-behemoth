package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The `passiv_day_active` archetype, end to end: a grazer that roams and forages by day, sleeps the night
 * through, ignores players entirely — and, once one of them draws blood, drops all of that and hunts them.
 *
 * The four capabilities behind it are general rather than special-cased for this profile, so each is asserted
 * as such: activity cycles (any archetype can be diurnal, nocturnal or neither), forage sensing (the writer
 * `KNOWN_VEGETATION` never had), the per-archetype aggro window, and posture reaching the client. The last
 * test is the one that keeps the generality honest — an archetype that names no cycle must behave exactly as
 * it did before any of this existed.
 */
class DayActiveScenarioTest {

  private lateinit var ai: AiPipelineFixture

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  @Test
  fun `a diurnal creature beds down at nightfall even when it is wide awake`() {
    ai.setNight()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))

    ai.tickUntilGoal(mob, "Sleep")

    // Not tiredness: it has barely been alive. Sleeping at all here is only expressible because the goal's
    // desired state includes RESTED — a tiredness ceiling on its own is already met by a wide-awake animal,
    // so the planner would have skipped the goal as satisfied.
    assertTrue(
      (ai.agentOf(mob).memory.get(BestiaDomain.TIREDNESS) ?: 0) <= BestiaDomain.RESTED_TIREDNESS,
      "the point of this case is that it is not tired",
    )
    assertEquals(Animation.AnimationKind.SLEEP, ai.animationOf(mob), "and the client should be told it is asleep")
  }

  @Test
  fun `it sleeps the whole night, not merely a nap`() {
    ai.setNight()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.tickUntilGoal(mob, "Sleep")

    // Comfortably past the sleep action's minimum. A fixed wait would have ended here and let the creature
    // wander off into the dark; the leaf stays RUNNING while the resting phase lasts.
    ai.tick(times = 20 * 30)

    assertEquals("Sleep", ai.goalNameOf(mob))
    assertEquals(Animation.AnimationKind.SLEEP, ai.animationOf(mob))
  }

  @Test
  fun `dawn wakes it and it gets on with its day`() {
    ai.setNight()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.tickUntilGoal(mob, "Sleep")

    ai.setDay()

    ai.tickUntil(describe = { "the creature never woke up (goal=${ai.goalNameOf(mob)})" }) {
      ai.goalNameOf(mob) != "Sleep"
    }
    // The posture is read off the *current* plan step rather than latched by the leaf that started it, which
    // is what makes this hold however the sleep ended.
    assertNotEquals(Animation.AnimationKind.SLEEP, ai.animationOf(mob), "posture must not stay latched")
  }

  @Test
  fun `a night's sleep actually rests it`() {
    ai.setNight()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.setDrive(mob, BestiaDomain.TIREDNESS, 95)
    ai.tickUntilGoal(mob, "Sleep")

    // Tiredness runs backwards while the posture says asleep, rather than jumping when the action reports
    // success. That is what makes a night that simply *ends* — dawn drops the goal, so the behaviour tree
    // never reports anything at all — still leave the creature rested.
    ai.tickUntil(describe = { "tiredness never recovered (t=${ai.agentOf(mob).memory.get(BestiaDomain.TIREDNESS)})" }) {
      (ai.agentOf(mob).memory.get(BestiaDomain.TIREDNESS) ?: 100) <= BestiaDomain.RESTED_TIREDNESS
    }

    // And it keeps sleeping regardless, because it is still night.
    assertEquals("Sleep", ai.goalNameOf(mob), "rested is not a reason to get up in the middle of the night")
  }

  @Test
  fun `it ignores a player standing right next to it`() {
    ai.setDay()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.spawnPlayer(Vec3L(1, 0, 0))

    // Long enough for several perception sweeps and several think periods.
    ai.tick(times = 20 * 5)

    assertEquals(true, ai.agentOf(mob).memory.get(BestiaDomain.ENEMY_IN_SIGHT), "it can see them perfectly well")
    assertEquals(false, ai.agentOf(mob).memory.get(BestiaDomain.IS_AGGRO))
    assertNotEquals("KillEnemy", ai.goalNameOf(mob), "seeing a player is not a reason to attack one")
    assertNotEquals("KillAttacker", ai.goalNameOf(mob))
  }

  @Test
  fun `being bitten wakes it and it goes after the attacker`() {
    ai.setNight()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    val player = ai.spawnPlayer(Vec3L(4, 0, 0))
    ai.tickUntilGoal(mob, "Sleep")

    ai.recordHit(victim = mob, attacker = player)

    // Retaliation outranks sleeping through the night, which is the ordering the two goals' priorities are
    // chosen against: a creature that slept through being eaten would be a bug reported as one.
    ai.tickUntilGoal(mob, "KillAttacker")
    assertNotEquals(Animation.AnimationKind.SLEEP, ai.animationOf(mob))

    val distanceWhenWoken = ai.distanceBetween(mob, player)
    ai.tickUntil(describe = { "it never closed on its attacker from $distanceWhenWoken" }) {
      ai.distanceBetween(mob, player) < distanceWhenWoken
    }
  }

  @Test
  fun `it hunts rather than flees, however badly hurt`() {
    ai.setDay()
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0), health = 10, maxHealth = 10)
    val player = ai.spawnPlayer(Vec3L(3, 0, 0))

    ai.recordHit(victim = mob, attacker = player)
    ai.tickUntilGoal(mob, "KillAttacker")

    // 10% of max would have any of the other archetypes running. This one has no Flee goal at all, and a goal
    // a profile does not list is one the creature cannot have — the temperament is in the goal list, not in a
    // threshold that could be tuned back into cowardice by accident.
    ai.setHealth(mob, 1)
    ai.tick(times = 20 * 5)

    assertNotEquals("Flee", ai.goalNameOf(mob))
    assertEquals("KillAttacker", ai.goalNameOf(mob))
  }

  @Test
  fun `it finds food on grazeable ground and eats it`() {
    ai.setDay()
    ai.grazeableGround = true
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.setDrive(mob, BestiaDomain.HUNGER, 80)

    // Nothing seeds the vegetation map here: the forage sense system notices the creature is standing on
    // ground that will feed it. Before it existed, both foraging actions always grounded to nothing and a
    // hungry creature simply failed to plan.
    ai.tickUntil(describe = { "no vegetation was ever remembered" }) {
      ai.agentOf(mob).snapshotState(ai.sharedMemory.worldBoard())
        .get(BestiaDomain.KNOWN_VEGETATION).orEmpty().isNotEmpty()
    }

    ai.tickUntilGoal(mob, "EatVegetation")
    ai.tickUntil(describe = { "hunger was never spent (h=${ai.agentOf(mob).memory.get(BestiaDomain.HUNGER)})" }) {
      (ai.agentOf(mob).memory.get(BestiaDomain.HUNGER) ?: 100) <= 15
    }
  }

  @Test
  fun `barren ground feeds nobody`() {
    ai.setDay()
    ai.grazeableGround = false
    val mob = ai.spawnMob("passiv_day_active", Vec3L(0, 0, 0))
    ai.setDrive(mob, BestiaDomain.HUNGER, 80)

    ai.tick(times = 20 * 10)

    assertTrue(
      ai.agentOf(mob).snapshotState(ai.sharedMemory.worldBoard())
        .get(BestiaDomain.KNOWN_VEGETATION).orEmpty().isEmpty(),
      "a desert must not remember grass",
    )
  }

  @Test
  fun `an archetype that names no activity cycle is unaffected by nightfall`() {
    ai.setNight()
    val mob = ai.spawnMob("passive_wanderer", Vec3L(0, 0, 0))

    // Cathemeral is the default, and it has no resting phase: this archetype sleeps when tired and at no other
    // time, exactly as it did before activity cycles existed. It gets bored long before it gets sleepy.
    ai.tickUntilGoal(mob, "Wander")

    assertTrue(
      (ai.agentOf(mob).memory.get(BestiaDomain.TIREDNESS) ?: 100) <
        (ai.agentOf(mob).memory.get(BestiaDomain.TIREDNESS_THRESHOLD) ?: 0),
      "it should have chosen to amble about in the dark rather than to sleep",
    )
  }
}

package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.goal.GoalRegistry
import net.bestia.zone.ai.goal.UtilityScorer
import net.bestia.zone.ai.goal.consideration.ConsiderationInputRegistry
import net.bestia.zone.ai.goal.consideration.CurveRegistry
import net.bestia.zone.ai.goal.consideration.EnemyInSightInput
import net.bestia.zone.ai.goal.consideration.IdentityCurve
import net.bestia.zone.ai.goal.consideration.InverseCurve
import net.bestia.zone.ai.goal.consideration.LinearRisingCurve
import net.bestia.zone.ai.goal.consideration.OwnHealthPctInput
import net.bestia.zone.ai.goal.consideration.TraitInput
import net.bestia.zone.ai.goal.goals.FleeGoal
import net.bestia.zone.ai.goal.goals.KillEnemyGoal
import net.bestia.zone.ai.goal.goals.WanderGoal
import net.bestia.zone.ai.perception.PerceptionSystem
import net.bestia.zone.ai.planner.GoapActionRegistry
import net.bestia.zone.ai.planner.GoapPlanner
import net.bestia.zone.ai.planner.WorldStateBuilder
import net.bestia.zone.ai.planner.actions.ApproachTargetAction
import net.bestia.zone.ai.planner.actions.FleeToSafetyAction
import net.bestia.zone.ai.planner.actions.MeleeAttackAction
import net.bestia.zone.ai.planner.actions.WanderAction
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.AvailableSkills
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.ReceivedDamageSystem
import net.bestia.zone.ecs.movement.MoveSystem
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * End-to-end demonstration of the whole AI stack driving one mob through its full behavioural
 * lifecycle: idle wandering, spotting a player, chasing, attacking in melee, and finally fleeing at
 * low health.
 *
 * Unlike [AiBehaviorScenarioTest] (which ticks the AI stages once to check a single transition),
 * this test runs a faithful mini game-loop: all the real AI systems plus the real [MoveSystem] and
 * [ReceivedDamageSystem] registered in an ecs [World] and stepped at 20 tps, with
 * perception/think refreshed every ~0.5s exactly as the live engine loop does.
 */
class AiLifecycleE2ETest {

  private val tickRate = 20
  private val dt = 1f / tickRate // 0.05s per tick

  private lateinit var world: World
  private lateinit var aoi: EntityAOIService

  @BeforeEach
  fun setup() {
    world = World()
    aoi = EntityAOIService()

    val curveRegistry = CurveRegistry(listOf(IdentityCurve(), InverseCurve(), LinearRisingCurve()))
    val inputRegistry = ConsiderationInputRegistry(listOf(EnemyInSightInput(), OwnHealthPctInput(), TraitInput()))
    val goalRegistry = GoalRegistry(listOf(KillEnemyGoal(), FleeGoal(), WanderGoal()))
    val actionRegistry = GoapActionRegistry(
      listOf(ApproachTargetAction(), MeleeAttackAction(), FleeToSafetyAction(), WanderAction())
    )
    val profileRegistry = AiProfileRegistry(curveRegistry, inputRegistry, goalRegistry, actionRegistry)
    profileRegistry.load()

    // Register the whole pipeline in the world; the scheduler runs them in registration order.
    world.addSystem(PerceptionSystem(profileRegistry, aoi))
    world.addSystem(
      AiThinkSystem(
        profileRegistry,
        UtilityScorer(inputRegistry, curveRegistry, goalRegistry),
        WorldStateBuilder(),
        GoapPlanner(),
        actionRegistry
      )
    )
    world.addSystem(AiActSystem())
    world.addSystem(MoveSystem())
    world.addSystem(ReceivedDamageSystem())
  }

  @Test
  fun `a blob wanders, hunts the player, then flees when hurt`() {
    // ---- Spawn a lone blob: no player exists yet, so nothing is in its area of interest. ----
    val blob = spawnBlob(Vec3L(0, 0, 0), health = 10)

    // Phase 1 — IDLE: with no enemy perceived, utility scoring falls back to wandering.
    run(ticks = 30)
    assertEquals("idle_wander", goalNameOf(blob), "a blob with nobody around should wander")

    // ---- A player appears within sight. ----
    val player = spawnPlayer(Vec3L(4, 0, 0), health = 30)

    // Phase 2 — HUNT: the blob perceives the player, adopts kill_enemy, chases and hits it in melee.
    run(ticks = 200)

    assertEquals("kill_enemy", goalNameOf(blob), "with a healthy body and a target, the blob hunts")
    assertTrue(
      distanceBetween(blob, player) <= 1,
      "the blob should have closed to melee range (was ${distanceBetween(blob, player)})"
    )
    val playerHp = healthOf(player)
    assertTrue(playerHp < 30, "the player should have taken melee damage (hp=$playerHp)")

    // ---- The blob is badly wounded. ----
    setHealth(blob, 2) // 20% of max, below the flee threshold
    val distanceWhenHurt = distanceBetween(blob, player)

    // Phase 3 — FLEE: low health flips the winning goal and the blob retreats from the threat.
    run(ticks = 20) // enough ticks to guarantee a perception/think re-decision
    assertEquals("flee", goalNameOf(blob), "a wounded blob should decide to flee")

    run(ticks = 60)
    assertTrue(
      distanceBetween(blob, player) > distanceWhenHurt,
      "the fleeing blob should increase its distance from the player " +
        "(was $distanceWhenHurt, now ${distanceBetween(blob, player)})"
    )
  }

  // ---- Mini game-loop: the world scheduler runs the registered systems each tick. ----

  private fun run(ticks: Int) {
    repeat(ticks) { world.tick(dt) }
  }

  // ---- Spawn + inspection helpers ----

  private fun spawnBlob(pos: Vec3L, health: Int): EntityId =
    world.createEntity { id ->
      world.add(id, Position.fromVec3(pos))
      world.add(id, Health(health, 10))
      world.add(id, Speed())
      world.add(id, Brain("aggressive_melee", homePosition = pos))
      world.add(id, AvailableSkills(mutableMapOf(0L to 1)))
    }

  private fun spawnPlayer(pos: Vec3L, health: Int): EntityId {
    val id = world.createEntity { eid ->
      world.add(eid, Position.fromVec3(pos))
      world.add(eid, Health(health, health))
      world.add(eid, Master(1L))
    }
    aoi.setEntityPosition(id, pos) // players remain stationary in this scenario
    return id
  }

  private fun goalNameOf(id: EntityId): String? = world.getOrThrow(id, Brain::class).currentGoal?.name

  private fun healthOf(id: EntityId): Int = world.getOrThrow(id, Health::class).current

  private fun setHealth(id: EntityId, value: Int) {
    world.getOrThrow(id, Health::class).current = value
  }

  private fun positionOf(id: EntityId): Vec3L = world.getOrThrow(id, Position::class).toVec3L()

  private fun distanceBetween(a: EntityId, b: EntityId): Long = positionOf(a).distance(positionOf(b))
}

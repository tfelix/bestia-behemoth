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
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * In-process exercise of the full AI pipeline against a real ecs [World]: spawn a brain-equipped
 * mob and a player entity, run perception -> think -> act by hand, and assert the mob acquires the
 * target, plans, moves, attacks in melee range, and flips to fleeing at low health.
 */
class AiBehaviorScenarioTest {

  private lateinit var world: World
  private lateinit var aoi: EntityAOIService
  private lateinit var perceptionSystem: PerceptionSystem
  private lateinit var thinkSystem: AiThinkSystem
  private lateinit var actSystem: AiActSystem

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

    perceptionSystem = PerceptionSystem(profileRegistry, aoi)
    thinkSystem = AiThinkSystem(
      profileRegistry,
      UtilityScorer(inputRegistry, curveRegistry, goalRegistry),
      WorldStateBuilder(),
      GoapPlanner(),
      actionRegistry
    )
    actSystem = AiActSystem()
  }

  private fun spawnMob(pos: Vec3L, health: Int): EntityId =
    world.createEntity { id ->
      world.add(id, Position.fromVec3(pos))
      world.add(id, Health(health, 10))
      world.add(id, Speed())
      world.add(id, Brain("aggressive_melee", homePosition = pos))
      world.add(id, AvailableSkills(mutableMapOf(0L to 1)))
    }

  private fun spawnPlayer(pos: Vec3L): EntityId {
    val id = world.createEntity { eid ->
      world.add(eid, Position.fromVec3(pos))
      world.add(eid, Health(30, 30))
      world.add(eid, Master(1L))
    }
    aoi.setEntityPosition(id, pos)
    return id
  }

  private fun perceive() = perceptionSystem.update(world, 0.5f)
  private fun think() = thinkSystem.update(world, 0.5f)
  private fun act() = actSystem.update(world, 0.05f)

  private fun brainOf(id: EntityId): Brain = world.getOrThrow(id, Brain::class)

  @Test
  fun `mob acquires target, plans to kill and moves toward the player`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 10)
    val player = spawnPlayer(Vec3L(3, 0, 0))

    perceive()
    val afterPerceive = brainOf(mob)
    assertEquals(player, afterPerceive.targetId)
    assertEquals(Vec3L(3, 0, 0), afterPerceive.targetPosition)

    think()
    val afterThink = brainOf(mob)
    assertEquals("kill_enemy", afterThink.currentGoal?.name)
    assertEquals(
      listOf("approach_target", "melee_attack"),
      afterThink.currentPlan?.actions?.map { it.id }
    )

    act()
    val path = world.get(mob, Path::class)?.path
    assertNotNull(path, "mob should have started a path toward the player")
    assertTrue(path!!.first().x > 0, "mob should step toward the player on +x")
  }

  @Test
  fun `mob deals damage when the player is in melee range`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 10)
    val player = spawnPlayer(Vec3L(1, 0, 0))

    perceive()
    think()
    act()

    val damageTotal = world.get(player, Damage::class)?.total() ?: 0
    assertTrue(damageTotal > 0, "player should have taken melee damage")
  }

  @Test
  fun `mob flees when its own health is low`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 2) // 20% of max -> below flee threshold
    spawnPlayer(Vec3L(1, 0, 0))

    perceive()
    think()
    val brain = brainOf(mob)
    assertEquals("flee", brain.currentGoal?.name)

    act()
    val path = world.get(mob, Path::class)?.path
    assertNotNull(path, "fleeing mob should have a path")
    assertTrue(path!!.first().x < 0, "mob should step away from the player (negative x)")
  }
}

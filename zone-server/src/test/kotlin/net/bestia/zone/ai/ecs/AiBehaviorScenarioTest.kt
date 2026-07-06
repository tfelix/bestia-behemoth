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
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * In-process exercise of the full AI pipeline against a real (manually ticked) [ZoneServer]: spawn a
 * brain-equipped mob and a player entity, run perception -> think -> act by hand, and assert the mob
 * acquires the target, plans, moves, attacks in melee range, and flips to fleeing at low health.
 */
class AiBehaviorScenarioTest {

  private lateinit var zone: ZoneServer
  private lateinit var aoi: EntityAOIService
  private lateinit var perceptionSystem: PerceptionSystem
  private lateinit var thinkSystem: AiThinkSystem
  private lateinit var actSystem: AiActSystem

  @BeforeEach
  fun setup() {
    aoi = EntityAOIService()

    val curveRegistry = CurveRegistry(listOf(IdentityCurve(), InverseCurve(), LinearRisingCurve()))
    val inputRegistry = ConsiderationInputRegistry(listOf(EnemyInSightInput(), OwnHealthPctInput(), TraitInput()))
    val goalRegistry = GoalRegistry(listOf(KillEnemyGoal(), FleeGoal(), WanderGoal()))
    val actionRegistry = GoapActionRegistry(
      listOf(ApproachTargetAction(), MeleeAttackAction(), FleeToSafetyAction(), WanderAction())
    )
    val profileRegistry = AiProfileRegistry(curveRegistry, inputRegistry, goalRegistry, actionRegistry)
    profileRegistry.load()

    zone = ZoneServer(ZoneConfig(tickRate = 20), emptyList(), emptyList(), emptyList())
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
    zone.addEntityWithWriteLock { entity ->
      entity.addAll(
        Position.fromVec3(pos),
        Health(health, 10),
        Speed(),
        Brain("aggressive_melee"),
        AvailableAttacks(mutableMapOf(0L to 1))
      )
    }

  private fun spawnPlayer(pos: Vec3L): EntityId {
    val id = zone.addEntityWithWriteLock { entity ->
      entity.addAll(Position.fromVec3(pos), Health(30, 30), Master(1L))
    }
    aoi.setEntityPosition(id, pos)
    return id
  }

  private fun perceive(id: EntityId) = zone.withEntityWriteLock(id) { perceptionSystem.update(0.5f, it, zone) }
  private fun think(id: EntityId) = zone.withEntityWriteLock(id) { thinkSystem.update(0.5f, it, zone) }
  private fun act(id: EntityId) = zone.withEntityWriteLock(id) { actSystem.update(0.05f, it, zone) }

  private fun brainOf(id: EntityId): Brain =
    zone.withEntityReadLockOrThrow(id) { it.getOrThrow(Brain::class) }

  @Test
  fun `mob acquires target, plans to kill and moves toward the player`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 10)
    val player = spawnPlayer(Vec3L(3, 0, 0))

    perceive(mob)
    val afterPerceive = brainOf(mob)
    assertEquals(player, afterPerceive.targetId)
    assertEquals(Vec3L(3, 0, 0), afterPerceive.targetPosition)

    think(mob)
    val afterThink = brainOf(mob)
    assertEquals("kill_enemy", afterThink.currentGoal?.name)
    assertEquals(
      listOf("approach_target", "melee_attack"),
      afterThink.currentPlan?.actions?.map { it.id }
    )

    act(mob)
    val path = zone.withEntityReadLockOrThrow(mob) { it.get(Path::class)?.path }
    assertNotNull(path, "mob should have started a path toward the player")
    assertTrue(path!!.first().x > 0, "mob should step toward the player on +x")
  }

  @Test
  fun `mob deals damage when the player is in melee range`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 10)
    val player = spawnPlayer(Vec3L(1, 0, 0))

    perceive(mob)
    think(mob)
    act(mob)

    val damageTotal = zone.withEntityReadLockOrThrow(player) { it.get(Damage::class)?.total() ?: 0 }
    assertTrue(damageTotal > 0, "player should have taken melee damage")
  }

  @Test
  fun `mob flees when its own health is low`() {
    val mob = spawnMob(Vec3L(0, 0, 0), health = 2) // 20% of max -> below flee threshold
    spawnPlayer(Vec3L(1, 0, 0))

    perceive(mob)
    think(mob)
    val brain = brainOf(mob)
    assertEquals("flee", brain.currentGoal?.name)

    act(mob)
    val path = zone.withEntityReadLockOrThrow(mob) { it.get(Path::class)?.path }
    assertNotNull(path, "fleeing mob should have a path")
    assertTrue(path!!.first().x < 0, "mob should step away from the player (negative x)")
  }
}

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
import net.bestia.zone.ecs.IteratingSystem
import net.bestia.zone.ecs.PeriodicSystem
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.battle.AvailableAttacks
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.ReceivedDamageSystem
import net.bestia.zone.ecs.movement.MoveSystem
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Master
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
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
 * this test runs a faithful mini game-loop: the real AI systems plus the real [MoveSystem] and
 * [ReceivedDamageSystem], stepped at 20 tps, with perception/think refreshed every 0.5s exactly as
 * the live [ZoneServer] loop does. It doubles as usage documentation for the module.
 */
class AiLifecycleE2ETest {

  private val tickRate = 20
  private val dt = 1f / tickRate               // 0.05s per tick
  private val brainEveryNTicks = 10            // perception + think run every 0.5s

  private lateinit var zone: ZoneServer
  private lateinit var aoi: EntityAOIService

  private lateinit var perceptionSystem: PerceptionSystem
  private lateinit var thinkSystem: AiThinkSystem
  private lateinit var actSystem: AiActSystem
  private lateinit var moveSystem: MoveSystem
  private lateinit var damageSystem: ReceivedDamageSystem

  private var tickCount = 0

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

    zone = ZoneServer(ZoneConfig(tickRate = tickRate), emptyList(), emptyList(), emptyList())
    perceptionSystem = PerceptionSystem(profileRegistry, aoi)
    thinkSystem = AiThinkSystem(
      profileRegistry,
      UtilityScorer(inputRegistry, curveRegistry, goalRegistry),
      WorldStateBuilder(),
      GoapPlanner(),
      actionRegistry
    )
    actSystem = AiActSystem()
    moveSystem = MoveSystem()
    damageSystem = ReceivedDamageSystem()
  }

  @Test
  fun `a blob wanders, hunts the player, then flees when hurt`() {
    // ---- Spawn a lone blob: no player exists yet, so nothing is in its area of interest. ----
    val blob = spawnBlob(Vec3L(0, 0, 0), health = 10)

    // Phase 1 — IDLE: with no enemy perceived, utility scoring falls back to wandering.
    run(ticks = 20, entities = listOf(blob))
    assertEquals("idle_wander", goalNameOf(blob), "a blob with nobody around should wander")

    // ---- A player appears within sight. ----
    val player = spawnPlayer(Vec3L(4, 0, 0), health = 30)
    val entities = listOf(blob, player)

    // Phase 2 — HUNT: the blob perceives the player, adopts kill_enemy, chases and hits it in melee.
    run(ticks = 160, entities = entities)

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
    run(ticks = 4, entities = entities) // one brain tick to re-decide
    assertEquals("flee", goalNameOf(blob), "a wounded blob should decide to flee")

    run(ticks = 40, entities = entities)
    assertTrue(
      distanceBetween(blob, player) > distanceWhenHurt,
      "the fleeing blob should increase its distance from the player " +
        "(was $distanceWhenHurt, now ${distanceBetween(blob, player)})"
    )
  }

  // ---- Mini game-loop: mirrors ZoneServer's tick ordering for the entities under test. ----

  private fun run(ticks: Int, entities: List<EntityId>) {
    repeat(ticks) {
      val brainTick = tickCount % brainEveryNTicks == 0
      if (brainTick) {
        periodic(perceptionSystem, entities)
        periodic(thinkSystem, entities)
      }
      iterating(actSystem, entities)
      iterating(moveSystem, entities)
      iterating(damageSystem, entities)
      tickCount++
    }
  }

  private fun periodic(system: PeriodicSystem, entities: List<EntityId>) {
    entities.forEach { id ->
      zone.withEntityWriteLock(id) { e -> if (system.entityMatches(e)) system.update(0.5f, e, zone) }
    }
  }

  private fun iterating(system: IteratingSystem, entities: List<EntityId>) {
    entities.forEach { id ->
      zone.withEntityWriteLock(id) { e -> if (system.entityMatches(e)) system.update(dt, e, zone) }
    }
  }

  // ---- Spawn + inspection helpers ----

  private fun spawnBlob(pos: Vec3L, health: Int): EntityId =
    zone.addEntityWithWriteLock { entity ->
      entity.addAll(
        Position.fromVec3(pos),
        Health(health, 10),
        Speed(),
        Brain("aggressive_melee"),
        AvailableAttacks(mutableMapOf(0L to 1))
      )
    }

  private fun spawnPlayer(pos: Vec3L, health: Int): EntityId {
    val id = zone.addEntityWithWriteLock { entity ->
      entity.addAll(Position.fromVec3(pos), Health(health, health), Master(1L))
    }
    aoi.setEntityPosition(id, pos) // players remain stationary in this scenario
    return id
  }

  private fun goalNameOf(id: EntityId): String? =
    zone.withEntityReadLockOrThrow(id) { it.getOrThrow(Brain::class).currentGoal?.name }

  private fun healthOf(id: EntityId): Int =
    zone.withEntityReadLockOrThrow(id) { it.getOrThrow(Health::class).current }

  private fun setHealth(id: EntityId, value: Int) {
    zone.withEntityWriteLock(id) { it.getOrThrow(Health::class).current = value }
  }

  private fun positionOf(id: EntityId): Vec3L =
    zone.withEntityReadLockOrThrow(id) { it.getOrThrow(Position::class).toVec3L() }

  private fun distanceBetween(a: EntityId, b: EntityId): Long = positionOf(a).distance(positionOf(b))
}

package net.bestia.zone.ai.ecs

import io.mockk.mockk
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.perception.PerceptionSystem
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.MoveSystem
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.util.EntityId

/**
 * The whole AI pipeline wired for a test, without a Spring context and without a generated world.
 *
 * `SkillExecutionService` is mocked rather than built. Constructing a real one means a skill repository, a
 * strategy factory, a battle-context factory, an outbound message processor and a status-effect service —
 * none of which an AI test has an opinion about. What an AI test *does* have an opinion about is whether the
 * creature decided to attack the right target with the right skill, and a mock records exactly that. Damage
 * arithmetic is the battle system's business and is tested there.
 */
class AiPipelineFixture(tickRate: Int = 20) {

  val aoi = EntityAOIService()
  val skills: SkillExecutionService = mockk(relaxed = true)
  val sharedMemory = SharedMemoryService()

  val profiles = AiProfileRegistry().apply { load() }

  val agentFactory = AiAgentFactory(
    navigation = TestNavigation.service(),
    skills = skills,
    sharedMemory = sharedMemory,
  )

  /** The AI stages in pipeline order, plus movement so a decision to walk actually moves something. */
  val systems: List<System> = listOf(
    PerceptionSystem(profiles, aoi),
    AiDriveSystem(sharedMemory),
    AiThinkSystem(Planner(), sharedMemory),
    AiActSystem(sharedMemory, ZoneConfig(tickRate = tickRate)),
    // No terrain in these scenarios, so no ground to snap to; null keeps the waypoint's own z, which is what
    // the flat test navigation produces anyway.
    MoveSystem { _, _ -> null },
  )

  val world: World = testWorld(systems = systems)

  /** A mob running [profileId], at [pos], with the basic attack seeded the way the real spawner does. */
  fun spawnMob(profileId: String, pos: Vec3L, health: Int = 10, maxHealth: Int = 10): EntityId =
    world.createEntity { id ->
      world.add(id, Position.fromVec3(pos))
      world.add(id, Health(health, maxHealth))
      world.add(id, Speed())
      world.add(id, KnownSkills(mutableMapOf(0L to 1)))
      world.add(id, agentFactory.create(profiles.getOrThrow(profileId), homePosition = pos))
    }

  /** A player entity — `Master` is what the perception system currently treats as hostile. */
  fun spawnPlayer(pos: Vec3L, health: Int = 30): EntityId {
    val id = world.createEntity { eid ->
      world.add(eid, Position.fromVec3(pos))
      world.add(eid, Health(health, health))
      world.add(eid, Master(1L))
    }
    aoi.setEntityPosition(id, pos)
    return id
  }

  /** Records [attacker] having hit [victim], the signal retaliation is gated on. */
  fun recordHit(victim: EntityId, attacker: EntityId, damage: Int = 3) {
    val taken = world.get(victim, TakenDamage::class) ?: world.add(victim, TakenDamage())
    taken.addDamage(attacker, damage)
  }

  fun agentOf(id: EntityId): AiAgent = world.getOrThrow(id, AiAgent::class)

  fun goalNameOf(id: EntityId): String? = agentOf(id).currentGoal?.name

  fun positionOf(id: EntityId): Vec3L = world.getOrThrow(id, Position::class).toVec3L()

  fun distanceBetween(a: EntityId, b: EntityId): Long = positionOf(a).distance(positionOf(b))

  fun setHealth(id: EntityId, value: Int) {
    world.getOrThrow(id, Health::class).current = value
  }

  fun tick(times: Int = 1, dt: Float = 1f / 20) {
    repeat(times) { world.tick(dt) }
  }

  /**
   * Ticks until [condition] holds, up to [maxTicks], and fails the test with [describe] if it never does.
   *
   * Nearly every AI assertion needs this rather than a fixed tick count, because the pipeline is *paced*:
   * perception refreshes on a half-second schedule and each agent thinks on its own staggered period, so
   * nothing at all is decided in the first handful of ticks. Fixed counts either under-tick — asserting
   * against a blackboard perception has not filled in yet — or over-tick past the transition they meant to
   * catch, and both failures look like a broken AI rather than a badly paced test.
   */
  fun tickUntil(
    maxTicks: Int = 20 * 60,
    dt: Float = 1f / 20,
    describe: () -> String = { "condition never became true" },
    condition: () -> Boolean,
  ) {
    repeat(maxTicks) {
      if (condition()) return
      world.tick(dt)
    }
    if (!condition()) throw AssertionError("${describe()} within $maxTicks ticks")
  }

  /** Ticks until this agent has decided on [goalName]. */
  fun tickUntilGoal(id: EntityId, goalName: String, maxTicks: Int = 20 * 60) {
    tickUntil(maxTicks, describe = { "entity $id never adopted '$goalName' (last was ${goalNameOf(id)})" }) {
      goalNameOf(id) == goalName
    }
  }
}

package net.bestia.zone.ai.ecs

import net.bestia.zone.ecs.movement.GroundTrample
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ai.core.planner.Planner
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.domain.bestia.BestiaRuntime
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.domain.townsfolk.TownsfolkRuntime
import net.bestia.zone.ecs.spawn.townsfolk.IndoorRegistry
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import net.bestia.zone.ai.perception.ForageSense
import net.bestia.zone.ai.perception.PerceptionSystem
import net.bestia.zone.ai.perception.SenseSystem
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomainFixture
import net.bestia.zone.ai.domain.townsfolk.TownsfolkProduction
import net.bestia.zone.ai.perception.SettlementFood
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.economy.Trade
import net.bestia.zone.ai.perception.SettlementSense
import net.bestia.zone.ai.perception.ShelterSense
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.battle.skill.AttackExecutionService
import net.bestia.zone.battle.skill.SkillExecutionService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.spawn.ambient.AmbientSpawnConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.ecs.movement.MoveSystem
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.util.EntityId
import kotlin.random.Random

/**
 * The whole AI pipeline wired for a test, without a Spring context and without a generated world.
 *
 * Both attack services are mocked rather than built. Constructing a real `SkillExecutionService` means a skill
 * repository, a strategy factory, a context factory and a job executor — none of which an AI test has an
 * opinion about. What an AI test *does* have an opinion about is whether the creature decided to attack the
 * right target by the right route, and a mock records exactly that. Damage arithmetic is the battle system's
 * business and is tested there.
 */
class AiPipelineFixture(tickRate: Int = 20, randomSeed: Long = DEFAULT_SEED) {

  /**
   * The generator every wandering creature in this fixture draws from, seeded so a scenario is reproducible.
   *
   * Wandering used to come off `Random.Default`, which meant a scenario could pin the world, the clock and
   * the navigation and still not say where a mob would be a second later. That is not a theoretical
   * complaint: `AiLifecycleE2ETest`'s melee scenario spawns the player 6 tiles from a mob whose sight
   * reaches 8, and a single unlucky wander leg of 3 tiles or more carried the mob out of its own sight
   * radius before it could acquire the target - about one run in twelve, measured, in a fresh JVM. Once the
   * quarry is out of sight `PerceptionSystem` drops it and the mob falls back to its ordinary idle cadence,
   * which is one six-second amble every couple of minutes and will not find anybody again inside a test's
   * tick budget.
   *
   * Pass a different [randomSeed] to explore other draws; the point is that a given seed always replays.
   */
  private val random = Random(randomSeed)

  val aoi = EntityAOIService()
  val skills: SkillExecutionService = mockk(relaxed = true)
  val attackExecution: AttackExecutionService = mockk(relaxed = true)
  val sharedMemory = SharedMemoryService()

  val profiles = AiProfileRegistry().apply { load() }

  /**
   * What the world calendar reports. Move it with [advanceHours]/[setDay]/[setNight] rather than by waiting:
   * a Bestia day takes eight real-world hours, so no test could ever tick its way to nightfall.
   *
   * A whole date rather than an hour, because a day has to be able to roll over. Anything that happens once
   * a day latches against `CommonKeys.DAY_INDEX` and is cleared by that number moving, so on a calendar
   * pinned to day one such a latch never clears and the behaviour cannot be tested at all.
   */
  var now: BestiaDateTime = BestiaDateTime(year = 1, month = 1, day = 1, hour = NOON, minute = 0, second = 0)

  var hourOfDay: Int
    get() = now.hour
    set(value) {
      now = now.copy(hour = value)
    }

  /**
   * A calendar the test drives. `BestiaClock` is anchored to the persisted world row and there is no world
   * here, so the only options are a fake and not testing day/night at all.
   */
  val clock: BestiaClock = mockk<BestiaClock>().also {
    every { it.now() } answers { now }
    every { it.speedFactor } returns BestiaDateTime.SPEED_FACTOR
  }

  /**
   * Whether the ground feeds a grazer. Off by default so the foraging half stays out of the way of scenarios
   * that are not about it — a mob on barren ground remembers no spots and never selects `EatVegetation`.
   */
  var grazeableGround: Boolean = false

  /**
   * Doorsteps a frightened townsperson could run to. Empty by default, so a scenario that is not about
   * fighting never has anybody make for one - see [ShelterSense].
   */
  val doorsteps = mutableListOf<Vec3L>()

  /**
   * Where a hungry townsperson can buy a meal, and whether there is one to buy.
   *
   * None by default, so a scenario that is not about eating never has anybody set off for a counter -
   * see [SettlementSense].
   */
  var mealStall: SettlementFood.Stall? = null

  /**
   * What the townsperson's trade is and whether the town can supply it.
   *
   * Nobody has a recipe by default, so an ordinary scenario's shift is the plain stand-at-the-post kind.
   */
  var workshops: SettlementWork = TownsfolkDomainFixture.NO_TRADES

  /** Where the evening happens. None by default, so an ordinary scenario's people stay in. */
  var square: Vec3L? = null

  // Spring collects the domain runtimes in the live server; a test names them.
  val bestia = BestiaRuntime(
    navigation = TestNavigation.service(),
    skills = skills,
    attackExecution = attackExecution,
    random = random,
  )

  /** Where a townsperson goes when they walk through a door. Readable, so a test can assert on it. */
  val indoors = IndoorRegistry()

  /** What the visible workers have turned out today. Readable, so a scenario can assert on it. */
  val production = TownsfolkProduction()

  val townsfolk = TownsfolkRuntime(
    navigation = TestNavigation.service(),
    indoors = indoors,
    work = object : SettlementWork {
      override fun tradeOf(business: String?) = workshops.tradeOf(business)
      override fun canSupply(settlement: Int, trade: Trade) = workshops.canSupply(settlement, trade)
      override fun supplierNear(at: Vec3L, business: String?) = workshops.supplierNear(at, business)
    },
    production = production,
    random = random,
  )

  val agentFactory = AiAgentFactory(runtimes = listOf(bestia, townsfolk), sharedMemory = sharedMemory)

  /**
   * Throttling off, so a scenario measures behaviour rather than cadence.
   *
   * `factor = 1` is the documented off switch, and it means these tests exercise exactly the code path a
   * den mob takes on the live server. `AiThrottleTest` covers the throttled path on its own.
   */
  val throttle = AiThrottle(AmbientSpawnConfig(throttleFactor = 1))

  /** The AI stages in pipeline order, plus movement so a decision to walk actually moves something. */
  val systems: List<System> = listOf(
    PerceptionSystem(profiles, aoi, clock, throttle),
    // Spring collects the Sense beans in the live server; a test names the ones its scenario cares about.
    SenseSystem(
      listOf(
        ForageSense { grazeableGround },
        ShelterSense(aoi) { at, reach -> doorsteps.filter { it.distance(at) <= reach } },
        SettlementSense(
          food = { mealStall },
          work = object : SettlementWork {
            override fun tradeOf(business: String?) = workshops.tradeOf(business)
            override fun canSupply(settlement: Int, trade: Trade) = workshops.canSupply(settlement, trade)
            override fun supplierNear(at: Vec3L, business: String?) = workshops.supplierNear(at, business)
          },
          gathering = { square },
        ),
      ),
      sharedMemory,
      throttle
    ),
    AiDriveSystem(sharedMemory, clock),
    AiThinkSystem(Planner(), sharedMemory, throttle),
    AiActSystem(sharedMemory, ZoneConfig(tickRate = tickRate)),
    // No terrain in these scenarios, so no ground to snap to; null keeps the waypoint's own z, which is what
    // the flat test navigation produces anyway.
    MoveSystem({ null }, GroundTrample.NONE),
  )

  val world: World = testWorld(systems = systems)

  /**
   * A mob running [profileId], at [pos]. No `KnownSkills`: a basic attack is not a catalogued skill, which is
   * what the real spawner does too.
   */
  fun spawnMob(
    profileId: String,
    pos: Vec3L,
    health: Int = 10,
    maxHealth: Int = 10,
    memory: Blackboard = Blackboard(),
  ): EntityId =
    world.createEntity { id ->
      world.add(id, Position.fromVec3(pos))
      world.add(id, Health(health, maxHealth))
      world.add(id, Speed())
      world.add(id, Animation())
      world.add(id, agentFactory.create(profiles.getOrThrow(profileId), homePosition = pos, memory = memory))
    }

  /**
   * A townsperson with a trade, which is a fact about the individual rather than about the archetype - so
   * it goes into the blackboard the agent is built on, exactly as `BestiaEntitySpawner` does it.
   */
  fun spawnTownsfolk(
    occupation: Occupation,
    home: Vec3L,
    post: Vec3L? = null,
    /** A prop id gives them a door to go through at night; without one they lie down where they stand. */
    homeBuilding: Long? = null,
    identity: Long = TownsfolkIdentity.of(settlement = 1, household = 0, member = 0),
  ): EntityId {
    val memory = Blackboard().apply {
      set(TownsfolkDomain.OCCUPATION, occupation, Blackboard.PERMANENT)
      post?.let { set(TownsfolkDomain.WORK_POSITION, it, Blackboard.PERMANENT) }
      homeBuilding?.let { set(TownsfolkDomain.HOME_BUILDING, it, Blackboard.PERMANENT) }
    }

    val id = spawnMob("townsfolk_commoner", home, memory = memory)
    world.add(id, Townsfolk(identity))

    return id
  }

  /** Moves the calendar on by [hours], carrying into the next day, month and year as it goes. */
  fun advanceHours(hours: Int) {
    require(hours >= 0) { "the calendar only runs forwards, was $hours" }

    var day = (now.day - 1).toLong() + (now.hour + hours) / BestiaDateTime.HOURS_PER_DAY
    val hour = (now.hour + hours) % BestiaDateTime.HOURS_PER_DAY
    var month = (now.month - 1).toLong() + day / BestiaDateTime.DAYS_PER_MONTH
    day %= BestiaDateTime.DAYS_PER_MONTH
    val year = now.year + month / BestiaDateTime.MONTHS_PER_YEAR
    month %= BestiaDateTime.MONTHS_PER_YEAR

    now = now.copy(year = year, month = month.toInt() + 1, day = day.toInt() + 1, hour = hour)
  }

  /** Moves the calendar to [hour], tomorrow if that hour has already gone by today. */
  fun advanceTo(hour: Int) {
    val delta = hour - now.hour
    advanceHours(if (delta > 0) delta else delta + BestiaDateTime.HOURS_PER_DAY)
  }

  /** Puts the world calendar into the daytime portion of the Bestia day. */
  fun setDay() {
    hourOfDay = NOON
  }

  /** Puts the world calendar into full night, which straddles midnight — see [BestiaDateTime.isNight]. */
  fun setNight() {
    hourOfDay = MIDNIGHT
  }

  fun animationOf(id: EntityId): Animation.AnimationKind =
    world.getOrThrow(id, Animation::class).currentAnimation

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

  /** Puts an entity somewhere else with the AOI tree kept in step, which is what a walk does for real. */
  fun teleport(id: EntityId, to: Vec3L) {
    world.getOrThrow(id, Position::class).apply {
      x = to.x
      y = to.y
      z = to.z
    }
    aoi.setEntityPosition(id, to)
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

  /**
   * Forces one of the 0..100 drives, so a test about what a hungry creature does need not wait out the two
   * real minutes the drive system takes to make one hungry.
   *
   * [Blackboard.PERMANENT] because that is how the drive system stores them; anything else would be evicted
   * by the TTL sweep on the next tick.
   */
  fun setDrive(id: EntityId, key: StateKey<Int>, value: Int) {
    agentOf(id).memory.set(key, value, Blackboard.PERMANENT)
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

  /** What [key] currently says on this agent's own board, for asserting a latch directly. */
  fun <T> beliefOf(id: EntityId, key: StateKey<T>): T? {
    return agentOf(id).memory.get(key)
  }

  companion object {
    /**
     * An arbitrary but fixed seed, so every scenario that does not care replays identically anyway.
     *
     * Arbitrary is the point: it is not tuned to make any particular assertion pass, so a scenario that only
     * holds for this one draw is a scenario that was never really testing what it claimed.
     */
    const val DEFAULT_SEED = 20260914L

    /** Comfortably inside full day, which runs from dawn's end to dusk's start. */
    private const val NOON = 12

    /** Comfortably inside full night, which runs from before midnight to [BestiaDateTime.NIGHT_END_HOUR]. */
    private const val MIDNIGHT = 0
  }
}

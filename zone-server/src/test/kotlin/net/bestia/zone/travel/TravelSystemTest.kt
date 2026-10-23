package net.bestia.zone.travel

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.GroundTrample
import net.bestia.zone.movement.ecs.MoveSystem
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.NavigationService
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.local.LocalPathfindingService
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import net.bestia.zone.travel.knowledge.RouteSense
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.vicinity.VicinityIndex
import net.bestia.zone.world.vicinity.VicinitySystem
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TravelSystemTest {

  private val country = BridgeCountry()
  private val config = TravelConfig(stallAfter = Duration.ofSeconds(3), giveUpAfter = Duration.ofSeconds(10))
  private val knowledge = RouteKnowledge(config)
  private val master = Knower.Player(MASTER_ID)
  private val ended = mutableListOf<JourneyEndedEvent>()

  private lateinit var harness: Harness

  /** One world over [walkQuery] and [macroGraph], with travel, walking and route sight wired as in the game. */
  private inner class Harness(walkQuery: LocalWalkQuery, macroGraph: MacroGraphService, travelConfig: TravelConfig) {
    private val navigationConfig = NavigationConfig()
    private val navigation = NavigationService(
      macroGraph,
      LocalPathfindingService(walkQuery, navigationConfig),
      MovementProfileRegistry().apply { load() },
      navigationConfig,
    )
    private val budget = TravelBudget(travelConfig)
    private val events = ApplicationEventPublisher { ended.add(it as JourneyEndedEvent) }
    private val travel = TravelSystem(
      RoutePlanner(navigation, knowledge, navigationConfig),
      LegRefiner(navigation, budget, travelConfig, navigationConfig),
      ProgressWatchdog(knowledge, travelConfig),
      budget,
      events,
    )
    private val chunkService: ChunkService = mockk {
      every { isReady } returns true
      every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
      every { normalise(any()) } answers { firstArg() }
    }
    private val vicinityIndex = VicinityIndex()
    private val vicinity = VicinitySystem(chunkService, vicinityIndex, listOf(RouteSense(macroGraph, vicinityIndex, knowledge)))

    val world = testWorld(systems = listOf(travel, MoveSystem({ 0L }, GroundTrample.NONE, walkQuery)))
    val journeys = JourneyService(travelConfig, AttackCancelService())

    fun bestiaAt(position: Vec3L): EntityId {
      return world.createEntity { id ->
        world.add(id, Position.fromVec3(position))
        world.add(id, Speed())
        world.add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = id))
      }
    }

    fun tick() {
      world.tick(DT)
      vicinity.update(world, DT)
    }

    fun positionOf(id: EntityId): Vec3L {
      return world.getOrThrow(id, Position::class).toVec3L()
    }
  }

  private fun inBridgeCountry(): Harness {
    return Harness(country.walkQuery, country.macroGraph, config).also { harness = it }
  }

  private fun onOpenGround(walkable: (Vec3L) -> Boolean = { true }): Harness {
    val ground = TestNavigation.flatGround(walkable)
    val noGraph = MacroGraphService(TestNavigation.noMacroGraph(), ground, NavigationConfig())
    return Harness(ground, noGraph, config).also { harness = it }
  }

  /** Ticks until the journey ends, calling [watch] with the traveller's position on every tick. */
  private fun travelUntilEnded(id: EntityId, maxSeconds: Int = 900, watch: (Vec3L) -> Unit = {}): JourneyEndedEvent {
    repeat((maxSeconds / DT).toInt()) {
      harness.tick()
      watch(harness.positionOf(id))
      ended.firstOrNull { it.entityId == id }?.let { return it }
    }
    fail("The journey did not end within $maxSeconds s; it stood at ${harness.positionOf(id)}")
  }

  @Test
  fun `travels through every waypoint to the destination`() {
    val h = inBridgeCountry()
    val bestia = h.bestiaAt(BridgeCountry.WEST)
    val stop = Vec3L(60, 40, 0)
    var closestToStop = Long.MAX_VALUE

    h.journeys.start(h.world, bestia, listOf(stop, BridgeCountry.EAST), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) { closestToStop = minOf(closestToStop, distance(it, stop)) }

    assertNull(end.failure)
    assertTrue(closestToStop <= 1, "it went by the stop, closest $closestToStop")
    assertTrue(distance(h.positionOf(bestia), BridgeCountry.EAST) <= 1)
  }

  @Test
  fun `walks to a bridge it does not know has fallen, then goes round by the ford`() {
    val h = inBridgeCountry()
    country.breakBridge()
    val bestia = h.bestiaAt(BridgeCountry.WEST)
    var furthestEastOnTheRoad = 0L

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) { if (isOnTheWestRoad(it)) furthestEastOnTheRoad = maxOf(furthestEastOnTheRoad, it.x) }

    assertNull(end.failure)
    assertTrue(furthestEastOnTheRoad >= 90, "it turned back at x=$furthestEastOnTheRoad, before the bridge was in sight")
    assertTrue(knowledge.believesBlocked(master, country.bridgeEdge), "and its master now knows")
  }

  @Test
  fun `a bridge its master knows is down is never walked to`() {
    val h = inBridgeCountry()
    country.breakBridge()
    knowledge.observe(master, country.bridgeEdge, blocked = true)
    val bestia = h.bestiaAt(BridgeCountry.WEST)
    var furthestEastOnTheRoad = 0L

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) { if (isOnTheWestRoad(it)) furthestEastOnTheRoad = maxOf(furthestEastOnTheRoad, it.x) }

    assertNull(end.failure)
    assertTrue(furthestEastOnTheRoad < 60, "it went towards the bridge, to x=$furthestEastOnTheRoad")
  }

  @Test
  fun `with every way it knows blocked there is no route`() {
    val h = inBridgeCountry()
    knowledge.observe(master, country.bridgeEdge, blocked = true)
    knowledge.observe(master, FORD_EAST_EDGE, blocked = true)
    val bestia = h.bestiaAt(BridgeCountry.WEST)

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)

    assertEquals(JourneyFailure.NO_ROUTE, travelUntilEnded(bestia, maxSeconds = 1).failure)
  }

  @Test
  fun `walled in, it gives up as stuck`() {
    val pen = { at: Vec3L -> at.x in -5L..5L && at.y in -5L..5L }
    val h = onOpenGround(pen)
    val bestia = h.bestiaAt(Vec3L(0, 0, 0))

    h.journeys.start(h.world, bestia, listOf(Vec3L(40, 0, 0)), JourneyOrder.PLAYER)

    assertEquals(JourneyFailure.STUCK, travelUntilEnded(bestia, maxSeconds = 30).failure)
  }

  @Test
  fun `a wall built in front of it is walked round`() {
    var wallBuilt = false
    val wall = { at: Vec3L -> wallBuilt && at.x == 30L && at.y in -6L..6L }
    val h = onOpenGround { !wall(it) }
    val bestia = h.bestiaAt(Vec3L(0, 0, 0))

    h.journeys.start(h.world, bestia, listOf(Vec3L(60, 0, 0)), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) {
      if (it.x >= 10) wallBuilt = true
      if (wall(it)) fail("It walked through the wall at $it")
    }

    assertNull(end.failure)
  }

  @Test
  fun `a busy tick serves the traveller that waited longest`() {
    val h = Harness(
      TestNavigation.flatGround(),
      MacroGraphService(TestNavigation.noMacroGraph(), TestNavigation.flatGround(), NavigationConfig()),
      config.copy(pathfindsPerTick = 1),
    ).also { harness = it }
    val travellers = (0 until 3).map { h.bestiaAt(Vec3L(it * 100L, 0, 0)) }
    travellers.forEach { h.journeys.start(h.world, it, listOf(h.positionOf(it).copy(y = 20)), JourneyOrder.PLAYER) }

    repeat(3) { h.tick() }

    assertTrue(travellers.all { h.world.has(it, Path::class) }, "one search a tick still reached all three")
  }

  /** West of the river and near the road to the bridge, rather than out on the way round. */
  private fun isOnTheWestRoad(at: Vec3L): Boolean {
    return at.x < BridgeCountry.BRIDGE_WEST.x && at.y < 50
  }

  private fun distance(a: Vec3L, b: Vec3L): Long {
    return maxOf(Math.abs(a.x - b.x), Math.abs(a.y - b.y))
  }

  private companion object {
    const val MASTER_ID = 1L
    const val DT = 0.05f

    /** The way round, from the ford down to the east bank. */
    const val FORD_EAST_EDGE = 4
  }
}

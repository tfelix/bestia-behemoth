package net.bestia.zone.travel

import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
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

  private lateinit var harness: TravelTestWorld

  private fun inBridgeCountry(): TravelTestWorld {
    return TravelTestWorld(country.walkQuery, country.macroGraph, config, knowledge, ended::add).also { harness = it }
  }

  private fun onOpenGround(walkable: (Vec3L) -> Boolean = { true }): TravelTestWorld {
    return TravelTestWorld.onOpenGround(config, knowledge, ended::add, walkable).also { harness = it }
  }

  /** Ticks until the journey ends, calling [watch] with the traveller's position on every tick. */
  private fun travelUntilEnded(id: EntityId, maxSeconds: Int = 900, watch: (Vec3L) -> Unit = {}): JourneyEndedEvent {
    repeat((maxSeconds / TravelTestWorld.DT).toInt()) {
      harness.tick()
      watch(harness.positionOf(id))
      ended.firstOrNull { it.entityId == id }?.let { return it }
    }
    fail("The journey did not end within $maxSeconds s; it stood at ${harness.positionOf(id)}")
  }

  @Test
  fun `travels through every waypoint to the destination`() {
    val h = inBridgeCountry()
    val bestia = h.bestiaAt(MASTER_ID, BridgeCountry.WEST)
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
    val bestia = h.bestiaAt(MASTER_ID, BridgeCountry.WEST)
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
    val bestia = h.bestiaAt(MASTER_ID, BridgeCountry.WEST)
    var furthestEastOnTheRoad = 0L

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) { if (isOnTheWestRoad(it)) furthestEastOnTheRoad = maxOf(furthestEastOnTheRoad, it.x) }

    assertNull(end.failure)
    assertTrue(furthestEastOnTheRoad < 60, "it went towards the bridge, to x=$furthestEastOnTheRoad")
  }

  @Test
  fun `the owner's map is told the way ahead, bridge and all`() {
    val h = inBridgeCountry()
    val bestia = h.bestiaAt(MASTER_ID, BridgeCountry.WEST)

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)
    h.tick()

    val route = h.world.getOrThrow(bestia, Journey::class).route
    assertTrue(BridgeCountry.BRIDGE_WEST in route && BridgeCountry.BRIDGE_EAST in route, "route was $route")
    assertEquals(BridgeCountry.EAST, route.last())
  }

  @Test
  fun `with every way it knows blocked there is no route`() {
    val h = inBridgeCountry()
    knowledge.observe(master, country.bridgeEdge, blocked = true)
    knowledge.observe(master, FORD_EAST_EDGE, blocked = true)
    val bestia = h.bestiaAt(MASTER_ID, BridgeCountry.WEST)

    h.journeys.start(h.world, bestia, listOf(BridgeCountry.EAST), JourneyOrder.PLAYER)

    assertEquals(JourneyFailure.NO_ROUTE, travelUntilEnded(bestia, maxSeconds = 1).failure)
  }

  @Test
  fun `walled in, it gives up as stuck`() {
    val pen = { at: Vec3L -> at.x in -5L..5L && at.y in -5L..5L }
    val h = onOpenGround(pen)
    val bestia = h.bestiaAt(MASTER_ID, Vec3L(0, 0, 0))

    h.journeys.start(h.world, bestia, listOf(Vec3L(40, 0, 0)), JourneyOrder.PLAYER)

    assertEquals(JourneyFailure.STUCK, travelUntilEnded(bestia, maxSeconds = 30).failure)
  }

  @Test
  fun `a wall built in front of it is walked round`() {
    var wallBuilt = false
    val wall = { at: Vec3L -> wallBuilt && at.x == 30L && at.y in -6L..6L }
    val h = onOpenGround { !wall(it) }
    val bestia = h.bestiaAt(MASTER_ID, Vec3L(0, 0, 0))

    h.journeys.start(h.world, bestia, listOf(Vec3L(60, 0, 0)), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) {
      if (it.x >= 10) wallBuilt = true
      if (wall(it)) fail("It walked through the wall at $it")
    }

    assertNull(end.failure)
  }

  @Test
  fun `a busy tick serves the traveller that waited longest`() {
    val h = TravelTestWorld.onOpenGround(config.copy(pathfindsPerTick = 1), knowledge, ended::add)
      .also { harness = it }
    val travellers = (0 until 3).map { h.bestiaAt(MASTER_ID, Vec3L(it * 100L, 0, 0)) }
    travellers.forEach { h.journeys.start(h.world, it, listOf(h.positionOf(it).copy(y = 20)), JourneyOrder.PLAYER) }

    repeat(3) { h.tick() }

    assertTrue(travellers.all { h.world.has(it, Path::class) }, "one search a tick still reached all three")
  }

  @Test
  fun `a lake across the way is walked round`() {
    val h = onOpenGround(walkable = { !inLake(it) }, water = LAKE)
    val bestia = h.bestiaAt(MASTER_ID, Vec3L(0, 0, 0))
    val shore = Vec3L(600, 0, 0)

    h.journeys.start(h.world, bestia, listOf(shore), JourneyOrder.PLAYER)
    val end = travelUntilEnded(bestia) { if (inLake(it)) fail("It walked into the lake at $it") }

    assertNull(end.failure)
    assertTrue(distance(h.positionOf(bestia), shore) <= 1)
  }

  @Test
  fun `an island the lake rings is no route, and said so at once`() {
    val ring = { at: Vec3L -> Math.hypot(at.x - 300.0, at.y.toDouble()) in 100.0..260.0 }
    val h = onOpenGround(walkable = { !ring(it) }, water = waterWhere { x, y -> ring(Vec3L(x.toLong(), y.toLong(), 0)) })
    val bestia = h.bestiaAt(MASTER_ID, Vec3L(0, 0, 0))

    h.journeys.start(h.world, bestia, listOf(Vec3L(300, 0, 0)), JourneyOrder.PLAYER)

    assertEquals(JourneyFailure.NO_ROUTE, travelUntilEnded(bestia, maxSeconds = 1).failure)
  }

  @Test
  fun `a waypoint in the lake is refused before the journey starts`() {
    val h = onOpenGround(walkable = { !inLake(it) }, water = LAKE)
    val bestia = h.bestiaAt(MASTER_ID, Vec3L(0, 0, 0))

    val refusal = h.journeys.start(h.world, bestia, listOf(Vec3L(300, 0, 0)), JourneyOrder.PLAYER)

    assertEquals(JourneyRefusal.IN_WATER, refusal)
  }

  private fun onOpenGround(walkable: (Vec3L) -> Boolean, water: WaterSource): TravelTestWorld {
    return TravelTestWorld.onOpenGround(config, knowledge, ended::add, walkable, water).also { harness = it }
  }

  private fun inLake(at: Vec3L) = Math.hypot(at.x - 300.0, at.y.toDouble()) <= LAKE_RADIUS

  /** West of the river and near the road to the bridge, rather than out on the way round. */
  private fun isOnTheWestRoad(at: Vec3L): Boolean {
    return at.x < BridgeCountry.BRIDGE_WEST.x && at.y < 50
  }

  private fun distance(a: Vec3L, b: Vec3L): Long {
    return maxOf(Math.abs(a.x - b.x), Math.abs(a.y - b.y))
  }

  private companion object {
    const val MASTER_ID = 1L

    /** The way round, from the ford down to the east bank. */
    const val FORD_EAST_EDGE = 4

    /** Wider than a local search can see round: walked straight at, the far shore is never found. */
    const val LAKE_RADIUS = 200.0

    val LAKE = waterWhere { x, y -> Math.hypot(x - 300.0, y) <= LAKE_RADIUS }

    fun waterWhere(isDeep: (Double, Double) -> Boolean) = object : WaterSource {
      override val isReady get() = true
      override fun survey(area: Aabb, exact: Boolean) = WaterSurvey(isDeep, emptyList())
    }
  }
}

package net.bestia.zone.control.net

import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.logout.ecs.LogoutCancelService
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.control.ClientLeg
import net.bestia.zone.control.MoveRequestRateLimit
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.travel.TravelConfig

class MoveEntityHandlerTest {

  private val accountId = 1L
  private val connectionInfoService = ConnectionInfoService()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  /** Every step allowed: adjacency and slope both pass. Stands in for a NoQuery world's open floor. */
  private class OpenWalkQuery : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = true
    override fun surfaceAt(position: Vec3L) = position.z
    override fun isResident(position: Vec3L) = true
  }

  /** Refuses to step onto one named voxel column - a wall or a too-steep rise for a test to walk into. */
  private class WalledWalkQuery(private val blockedTo: Vec3L) : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = to != blockedTo
    override fun surfaceAt(position: Vec3L) = position.z
    override fun isResident(position: Vec3L) = true
  }

  /**
   * Would refuse every step if asked, but reports every column as non-resident - a chunk nothing has ever
   * queried a derived walkability tile for, same as a player's own chunk moments after their manifest
   * streamed it. Stands in for the fresh-spawn regression: a step must not be blocked on this alone.
   */
  private class NeverResidentWalkQuery : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = false
    override fun surfaceAt(position: Vec3L) = null
    override fun isResident(position: Vec3L) = false
  }

  /**
   * Ground at z = 0 everywhere and a wall at [wallX]. Only the slab near the ground is loaded, so asking about a
   * step at a made-up height finds nothing loaded there - which used to be read as "nothing to check".
   */
  private class GroundLevelWalkQuery(private val wallX: Long) : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L) = to.x != wallX
    override fun surfaceAt(position: Vec3L): Long = 0
    override fun isResident(position: Vec3L) = abs(position.z) < 10
  }

  private fun handlerFor(
    world: EcsWorld,
    entityId: EntityId,
    walkQuery: LocalWalkQuery,
    rateLimit: MoveRequestRateLimit = MoveRequestRateLimit(WorldRulesConfig(tickRate = 20)),
    water: WaterSource = WaterSource.NONE,
  ): MoveEntityHandler {
    connectionInfoService.activateSession(accountId, masterId = 1L, masterEntityId = entityId)

    return MoveEntityHandler(
      connectionInfoService = connectionInfoService,
      logoutCancelService = LogoutCancelService(),
      castCancelService = CastCancelService(),
      attackCancelService = AttackCancelService(),
      deadActionGuard = DeadActionGuard(),
      rateLimit = rateLimit,
      journeys = JourneyService(TravelConfig(), AttackCancelService(), RegionGrid(water)),
      clientLeg = ClientLeg(walkQuery, WorldRulesConfig(tickRate = 20)),
      outMessageProcessor = messages,
    )
  }

  @Test
  fun `a click into deep water is refused at once and said why`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val lake = object : WaterSource {
      override val isReady get() = true
      override fun survey(area: Aabb, exact: Boolean) = WaterSurvey({ x, _ -> x > 100.0 }, emptyList())
    }
    val handler = handlerFor(world, id, OpenWalkQuery(), water = lake)

    handler.handle(world, MoveEntityCMSG(accountId, id, waypoints = listOf(Vec3L(50, 0, 0), Vec3L(150, 0, 0))))

    assertNull(world.get(id, Journey::class), "it set off for the lake")
    verify { messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.TRAVEL_DESTINATION_IN_WATER)) }
  }

  /** A click the way the client sends one: to the last tile, with the steps it already walks there. */
  private fun moveAlong(id: EntityId, path: List<Vec3L>): MoveEntityCMSG {
    return MoveEntityCMSG(accountId, id, waypoints = listOfNotNull(path.lastOrNull()), firstLeg = path)
  }

  @Test
  fun `a fully walkable path is attached in full`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(world, moveAlong(id, path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  /** A walk east from the origin, one waypoint per tile, as the server holds it once a click is accepted. */
  private fun walkingEast(world: EcsWorld, handler: MoveEntityHandler, id: EntityId, tiles: Long) {
    handler.handle(world, moveAlong(id, (1L..tiles).map { Vec3L(it, 0, 0) }))
  }

  @Test
  fun `a route picked mid-walk joins the walk at the tile it starts on, keeping the step under way`() {
    // The client draws it from the tile it is stepping onto and sends that tile first, so both sides finish the
    // step they are on and turn there. Starting over instead stood the entity back on the tile it was leaving.
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    walkingEast(world, handler, id, 4)
    val walk = world.getOrThrow(id, Path::class)
    walk.stepRestarted()   // MoveSystem has set off on it

    val route = listOf(Vec3L(1, 0, 0), Vec3L(2, 1, 0), Vec3L(3, 2, 0))
    handler.handle(world, moveAlong(id, route))

    assertEquals(route, walk.path)
    assertFalse(walk.restartsStep, "the step towards (1, 0) is the one under way")
  }

  @Test
  fun `a client a few tiles ahead joins further along the walk`() {
    // Its prediction runs a trip ahead of this side, more after a slow tick. Measured from the position - the last
    // tile reached - this route starts three tiles away and used to be refused, which snapped the client back.
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    walkingEast(world, handler, id, 6)

    handler.handle(world, moveAlong(id, listOf(Vec3L(3, 0, 0), Vec3L(3, 1, 0))))

    assertEquals(
      listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0), Vec3L(3, 1, 0)),
      world.get(id, Path::class)?.path,
      "the tiles the client already walked are walked here too"
    )
  }

  @Test
  fun `a route that joins the walk is checked from where it joins`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(2, 1, 0)))
    walkingEast(world, handler, id, 4)

    handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0), Vec3L(2, 1, 0))))

    assertEquals(listOf(Vec3L(1, 0, 0)), world.get(id, Path::class)?.path, "the walk ends where the new route is walled")
  }

  @Test
  fun `a route that does not touch the walk starts from the entity's tile`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    walkingEast(world, handler, id, 4)

    val route = listOf(Vec3L(0, 1, 0), Vec3L(0, 2, 0))
    handler.handle(world, moveAlong(id, route))

    assertEquals(route, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a route that joins the walk is answered even when it changes nothing`() {
    // The client is already walking it and expects the echo; without one its prediction expires and snaps.
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    walkingEast(world, handler, id, 3)
    val path = world.getOrThrow(id, Path::class)
    path.clearDirty()

    handler.handle(world, moveAlong(id, path.path))

    assertTrue(path.isDirty())
  }

  @Test
  fun `a path is truncated at the first step a wall or slope refuses, not rejected outright`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(2, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(world, moveAlong(id, path))

    assertEquals(listOf(Vec3L(1, 0, 0)), world.get(id, Path::class)?.path)
  }

  /** Every step is checked under the world lock and the whole path is sent to every viewer. */
  @Test
  fun `a path longer than the cap is cut to the cap`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = (1L..100L).map { Vec3L(it, 0, 0) }
    handler.handle(world, moveAlong(id, path))

    assertEquals(path.take(WorldRulesConfig(tickRate = 20).maxMovePathSteps), world.get(id, Path::class)?.path)
  }

  @Test
  fun `a made-up step height does not get a path through a wall`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, GroundLevelWalkQuery(wallX = 2))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 9999), Vec3L(3, 0, 0))
    handler.handle(world, moveAlong(id, path))

    assertEquals(listOf(Vec3L(1, 0, 0)), world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when even its first step is refused`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(1, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, moveAlong(id, path))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `a step is not blocked by a wall or slope verdict from a column nothing has vouched for yet`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, NeverResidentWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, moveAlong(id, path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when its first step is not horizontally adjacent`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(5, 5, 0))
    handler.handle(world, moveAlong(id, path))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `a dropped path publishes the position, so the client stops asking from the wrong tile`() {
    // The refusal above is only half the job. The client drew that path from where it believes the entity
    // stands, so a silent drop leaves it believing the same thing and the next click produces the same
    // unreachable path: the player is stuck for good rather than for one click. A late tick is enough to open
    // the gap - `MoveSystem` steps several tiles on one overrunning delta, and neither the step nor the
    // waypoint it consumed goes on the wire.
    val world = testWorld()
    val id = world.createEntity { }
    val position = Position(0, 0, 0)
    world.add(id, position)

    // Fresh components are born dirty, so the flag has to be cleared to mean anything here.
    position.clearDirty()

    val handler = handlerFor(world, id, OpenWalkQuery())
    handler.handle(world, moveAlong(id, listOf(Vec3L(5, 5, 0))))

    assertNull(world.get(id, Path::class))
    assertTrue(position.isDirty(), "the entity's real position has to go out, or the next click fails too")
  }

  @Test
  fun `an empty path stops the entity by removing any current path`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    world.add(id, Path(mutableListOf(Vec3L(1, 0, 0))))
    val handler = handlerFor(world, id, OpenWalkQuery())

    handler.handle(world, moveAlong(id, emptyList()))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `an account over its request rate is ignored rather than served`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))

    // Two tokens and no refill, so the assertion does not depend on how long the test itself takes.
    val exhausted = MoveRequestRateLimit(
      WorldRulesConfig(tickRate = 20, moveRequestsPerSecond = 0f, moveRequestBurst = 2f)
    )
    val handler = handlerFor(world, id, OpenWalkQuery(), exhausted)

    repeat(2) {
      handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0))))
    }
    world.remove(id, Path::class)

    handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0))))

    assertNull(world.get(id, Path::class), "the request past the burst never reached the world")
  }

  @Test
  fun `a burst of clicking is served, because that is what clicking looks like`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    repeat(10) {
      handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0))))
    }

    assertTrue(world.get(id, Path::class) != null, "ten clicks is a person, not an attack")
  }

  @Test
  fun `an entity the account does not own is not moved`() {
    val world = testWorld()
    val master = world.createEntity { }
    world.add(master, Position(0, 0, 0))
    val stranger = world.createEntity { }
    world.add(stranger, Position(5, 0, 0))
    val handler = handlerFor(world, master, OpenWalkQuery())

    handler.handle(world, moveAlong(stranger, listOf(Vec3L(6, 0, 0))))

    assertFalse(world.has(stranger, Journey::class))
    assertFalse(world.has(stranger, Path::class))
  }

  @Test
  fun `an owned bestia is sent off without being the one driven`() {
    val world = testWorld()
    val master = world.createEntity { }
    world.add(master, Position(0, 0, 0))
    val bestia = world.createEntity { }
    world.add(bestia, Position(10, 0, 0))
    val handler = handlerFor(world, master, OpenWalkQuery())
    connectionInfoService.registerPlayerBestiaEntity(accountId, masterId = 1L, playerBestiaId = 7L, playerBestiaEntityId = bestia)

    handler.handle(world, MoveEntityCMSG(accountId, bestia, waypoints = listOf(Vec3L(500, 0, 0))))

    assertEquals(listOf(Vec3L(500, 0, 0)), world.get(bestia, Journey::class)?.waypoints)
    assertFalse(world.has(master, Journey::class), "the master stays where it is")
  }

  @Test
  fun `an appended waypoint goes on after the destination`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))))

    handler.handle(world, MoveEntityCMSG(accountId, id, waypoints = listOf(Vec3L(9, 9, 0)), append = true))

    assertEquals(listOf(Vec3L(2, 0, 0), Vec3L(9, 9, 0)), world.get(id, Journey::class)?.waypoints)
    val walk = world.get(id, Path::class)?.path
    assertEquals(listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0)), walk, "the walk under way goes on")
  }

  @Test
  fun `a stop ends the journey as well as the walk`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    handler.handle(world, moveAlong(id, listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))))

    handler.handle(world, MoveEntityCMSG(accountId, id, waypoints = emptyList()))

    assertFalse(world.has(id, Journey::class))
    assertFalse(world.has(id, Path::class))
  }
}

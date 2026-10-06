package net.bestia.zone.entity

import net.bestia.zone.ecs.battle.attack.AttackCancelService
import net.bestia.zone.ecs.battle.skill.CastCancelService
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bestia.zone.ecs.core.EcsWorld

class MoveActiveEntityHandlerTest {

  private val accountId = 1L

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
    rateLimit: MoveRequestRateLimit = MoveRequestRateLimit(ZoneConfig(tickRate = 20)),
  ): MoveActiveEntityHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(accountId, masterId = 1L, masterEntityId = entityId)

    return MoveActiveEntityHandler(
      connectionInfoService = connectionInfoService,
      logoutCancelService = LogoutCancelService(),
      castCancelService = CastCancelService(),
      attackCancelService = AttackCancelService(),
      deadActionGuard = DeadActionGuard(),
      walkQuery = walkQuery,
      zoneConfig = ZoneConfig(tickRate = 20),
      rateLimit = rateLimit,
    )
  }

  @Test
  fun `a fully walkable path is attached in full`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  @Test
  fun `an appended leg extends the walk under way from its last step`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))))

    handler.handle(world, 
      MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(3, 0, 0), Vec3L(4, 0, 0)), append = true)
    )

    assertEquals(
      listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0), Vec3L(4, 0, 0)),
      world.get(id, Path::class)?.path
    )
  }

  @Test
  fun `an appended leg that does not join the walk is ignored`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())
    val walk = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = walk))

    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(9, 0, 0)), append = true))

    assertEquals(walk, world.get(id, Path::class)?.path)
  }

  @Test
  fun `an appended leg after the walk ended starts a new walk from the position`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val leg = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = leg, append = true))

    assertEquals(leg, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is truncated at the first step a wall or slope refuses, not rejected outright`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(2, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

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
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(path.take(ZoneConfig(tickRate = 20).maxMovePathSteps), world.get(id, Path::class)?.path)
  }

  @Test
  fun `a made-up step height does not get a path through a wall`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, GroundLevelWalkQuery(wallX = 2))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 9999), Vec3L(3, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(listOf(Vec3L(1, 0, 0)), world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when even its first step is refused`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(1, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `a step is not blocked by a wall or slope verdict from a column nothing has vouched for yet`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, NeverResidentWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when its first step is not horizontally adjacent`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(5, 5, 0))
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = path))

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
    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(5, 5, 0))))

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

    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = emptyList()))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `an account over its request rate is ignored rather than served`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))

    // Two tokens and no refill, so the assertion does not depend on how long the test itself takes.
    val exhausted = MoveRequestRateLimit(
      ZoneConfig(tickRate = 20, moveRequestsPerSecond = 0f, moveRequestBurst = 2f)
    )
    val handler = handlerFor(world, id, OpenWalkQuery(), exhausted)

    repeat(2) {
      handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))
    }
    world.remove(id, Path::class)

    handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))

    assertNull(world.get(id, Path::class), "the request past the burst never reached the world")
  }

  @Test
  fun `a burst of clicking is served, because that is what clicking looks like`() {
    val world = testWorld()
    val id = world.createEntity { }
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    repeat(10) {
      handler.handle(world, MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))
    }

    assertTrue(world.get(id, Path::class) != null, "ten clicks is a person, not an attack")
  }
}

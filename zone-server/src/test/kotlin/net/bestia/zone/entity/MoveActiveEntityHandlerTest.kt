package net.bestia.zone.entity

import net.bestia.zone.ecs.battle.skill.CastCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

  private fun handlerFor(world: World, entityId: EntityId, walkQuery: LocalWalkQuery): MoveActiveEntityHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(accountId, masterId = 1L, masterEntityId = entityId)

    return MoveActiveEntityHandler(
      connectionInfoService = connectionInfoService,
      world = world,
      logoutCancelService = LogoutCancelService(world),
      castCancelService = CastCancelService(world),
      deadActionGuard = DeadActionGuard(world),
      walkQuery = walkQuery,
    )
  }

  @Test
  fun `a fully walkable path is attached in full`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is truncated at the first step a wall or slope refuses, not rejected outright`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(2, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0), Vec3L(3, 0, 0))
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(listOf(Vec3L(1, 0, 0)), world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when even its first step is refused`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, WalledWalkQuery(blockedTo = Vec3L(1, 0, 0)))

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `a step is not blocked by a wall or slope verdict from a column nothing has vouched for yet`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, NeverResidentWalkQuery())

    val path = listOf(Vec3L(1, 0, 0), Vec3L(2, 0, 0))
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = path))

    assertEquals(path, world.get(id, Path::class)?.path)
  }

  @Test
  fun `a path is dropped entirely when its first step is not horizontally adjacent`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val path = listOf(Vec3L(5, 5, 0))
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = path))

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
    val id = world.create()
    val position = Position(0, 0, 0)
    world.add(id, position)

    // Fresh components are born dirty, so the flag has to be cleared to mean anything here.
    position.clearDirty()

    val handler = handlerFor(world, id, OpenWalkQuery())
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(5, 5, 0))))

    assertNull(world.get(id, Path::class))
    assertTrue(position.isDirty(), "the entity's real position has to go out, or the next click fails too")
  }

  @Test
  fun `an empty path stops the entity by removing any current path`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    world.add(id, Path(mutableListOf(Vec3L(1, 0, 0))))
    val handler = handlerFor(world, id, OpenWalkQuery())

    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = emptyList()))

    assertNull(world.get(id, Path::class))
  }
}

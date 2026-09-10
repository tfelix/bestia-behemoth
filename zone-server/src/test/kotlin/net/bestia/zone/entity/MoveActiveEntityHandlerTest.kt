package net.bestia.zone.entity

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.ZoneConfig
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
import net.bestia.zone.world.stream.InterestRange
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

  private fun handlerFor(
    world: World,
    entityId: EntityId,
    walkQuery: LocalWalkQuery,
    rateLimit: MoveRequestRateLimit = MoveRequestRateLimit(ZoneConfig(tickRate = 20))
  ): MoveActiveEntityHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(accountId, masterId = 1L, masterEntityId = entityId)

    return MoveActiveEntityHandler(
      connectionInfoService = connectionInfoService,
      world = world,
      logoutCancelService = LogoutCancelService(world),
      castCancelService = CastCancelService(world),
      deadActionGuard = DeadActionGuard(world),
      walkQuery = walkQuery,
      // The real one derives this from the world's chunk size; the number is all this needs.
      interestRange = mockk { every { cubeEdge } returns VIEW_VOLUME_STEPS.toLong() },
      rateLimit = rateLimit,
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
  fun `an empty path stops the entity by removing any current path`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    world.add(id, Path(mutableListOf(Vec3L(1, 0, 0))))
    val handler = handlerFor(world, id, OpenWalkQuery())

    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = emptyList()))

    assertNull(world.get(id, Path::class))
  }

  @Test
  fun `a path longer than the view volume is cut down to it`() {
    // Nothing a click can produce. Unbounded, one 1 MB frame is some 35000 waypoints, all validated under the
    // world lock and then broadcast whole to everybody in range.
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    val marathon = (1..VIEW_VOLUME_STEPS * 3).map { Vec3L(it.toLong(), 0, 0) }
    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = marathon))

    assertEquals(VIEW_VOLUME_STEPS, world.get(id, Path::class)?.path?.size)
  }

  @Test
  fun `an account over its request rate is ignored rather than served`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))

    // Two tokens and no refill, so the assertion does not depend on how long the test itself takes.
    val exhausted = MoveRequestRateLimit(
      ZoneConfig(tickRate = 20, moveRequestsPerSecond = 0f, moveRequestBurst = 2f)
    )
    val handler = handlerFor(world, id, OpenWalkQuery(), exhausted)

    repeat(2) {
      handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))
    }
    world.remove(id, Path::class)

    handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))

    assertNull(world.get(id, Path::class), "the request past the burst never reached the world")
  }

  @Test
  fun `a burst of clicking is served, because that is what clicking looks like`() {
    val world = testWorld()
    val id = world.create()
    world.add(id, Position(0, 0, 0))
    val handler = handlerFor(world, id, OpenWalkQuery())

    repeat(10) {
      handler.handle(MoveActiveEntityCMSG(playerId = accountId, path = listOf(Vec3L(1, 0, 0))))
    }

    assertTrue(world.get(id, Path::class) != null, "ten clicks is a person, not an attack")
  }

  companion object {
    /** Stands in for InterestRange.cubeEdge, which is 352 at the shipped view radius. */
    private const val VIEW_VOLUME_STEPS = 352
  }
}

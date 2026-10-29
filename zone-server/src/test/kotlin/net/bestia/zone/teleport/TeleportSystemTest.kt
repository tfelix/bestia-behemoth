package net.bestia.zone.teleport

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityFx
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.GroundHeight
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.util.EntityId
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.world.SafeGround
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a teleporting body lands, and when. */
class TeleportSystemTest {

  private val start = Vec3L(10_000, 10_000, 70)
  private val settings = TeleportItemSettings()

  private lateinit var world: World
  private lateinit var out: OutMessageProcessor
  private lateinit var chunks: ChunkService
  private lateinit var subscriptions: ChunkSubscriptionService

  @BeforeEach
  fun setUp() {
    world = testWorld()
    out = mockk(relaxed = true)
    chunks = mockk()
    every { chunks.config } returns WorldConfig(seed = 1L)
    every { chunks.normalise(any()) } answers { firstArg() }
    subscriptions = mockk()
  }

  private fun system(
    ground: GroundHeight = GroundHeight { 77L },
    safeGround: SafeGround = SafeGround.NONE,
  ) = TeleportSystem(ground, chunks, subscriptions, settings, out, safeGround)

  private fun teleporting(destination: Teleporting.Destination, secondsUntilMove: Float = 0f): EntityId =
    world.createEntity { id ->
      add(id, Position.fromVec3(start))
      add(id, Account(ACCOUNT_ID))
      add(id, Path(mutableListOf(Vec3L(start.x + 1, start.y, start.z))))
      add(id, Teleporting(destination, secondsUntilMove))
    }

  private fun positionOf(id: EntityId) = world.get(id, Position::class)!!.toVec3L()

  @Test
  fun `a fly wing lands between the shortest hop and its radius, on the ground`() {
    repeat(50) {
      val id = teleporting(Teleporting.Destination.Nearby(150))

      system().update(world, 0f)

      val landed = positionOf(id)
      val distance = hypot((landed.x - start.x).toDouble(), (landed.y - start.y).toDouble())
      assertTrue(distance in 19.0..151.0, "landed $distance m away")
      assertEquals(77L, landed.z)
    }
  }

  @Test
  fun `the body appears once it has landed, and stops walking`() {
    val id = teleporting(Teleporting.Destination.Nearby(150))

    system().update(world, 0f)

    assertFalse(world.has(id, Teleporting::class))
    assertFalse(world.has(id, Path::class))
    assertEquals(EntityFx.Kind.WING_APPEAR, world.get(id, EntityFx::class)?.kind)
  }

  @Test
  fun `nothing moves before the vanish has played`() {
    val id = teleporting(Teleporting.Destination.Nearby(150), secondsUntilMove = 0.25f)

    system().update(world, 0.1f)

    assertEquals(start, positionOf(id))
    assertTrue(world.has(id, Teleporting::class))
  }

  @Test
  fun `only the body that used the wing moves`() {
    val user = teleporting(Teleporting.Destination.Nearby(150))
    val companion = world.createEntity { id -> add(id, Position.fromVec3(start)) }

    system().update(world, 0f)

    assertNotEquals(start, positionOf(user))
    assertEquals(start, positionOf(companion))
  }

  @Test
  fun `with no dry ground the wing fizzles and the body reappears where it stood`() {
    val id = teleporting(Teleporting.Destination.Nearby(150))
    val allWater = object : SafeGround {
      override fun isDry(x: Long, y: Long) = false
      override fun dryHome(): Vec3L? = null
    }

    system(safeGround = allWater).update(world, 0f)

    assertEquals(start, positionOf(id))
    assertEquals(EntityFx.Kind.WING_APPEAR, world.get(id, EntityFx::class)?.kind)
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TELEPORT_NO_LANDING)) }
  }

  /** What makes the hop need no loading screen: the client already holds the ground it lands on. */
  @Test
  fun `a body a player looks through only lands in chunks that player was sent`() {
    val id = teleporting(Teleporting.Destination.Nearby(150))
    world.add(id, ActivePlayer)
    every { subscriptions.sentTo(ACCOUNT_ID) } returns emptySet()

    system().update(world, 0f)

    assertEquals(start, positionOf(id))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TELEPORT_NO_LANDING)) }
  }

  @Test
  fun `a body lands in a sent chunk`() {
    val id = teleporting(Teleporting.Destination.Nearby(150))
    world.add(id, ActivePlayer)
    val everyChunkNearby = (305..320).flatMap { x -> (305..320).map { y -> ChunkPos(x, y, 0) } }.toSet()
    every { subscriptions.sentTo(ACCOUNT_ID) } returns everyChunkNearby

    system().update(world, 0f)

    assertNotEquals(start, positionOf(id))
  }

  @Test
  fun `a butterfly wing waits for its save point, then lands there`() {
    val home = Teleporting.Destination.Home()
    val id = teleporting(home)

    system().update(world, 0f)
    assertEquals(start, positionOf(id), "the lookup has not answered yet")

    home.savePoint = Vec3L(500, 600, 10)
    system().update(world, 0f)

    assertEquals(Vec3L(500, 600, 77), positionOf(id))
    assertEquals(EntityFx.Kind.HOME_APPEAR, world.get(id, EntityFx::class)?.kind)
  }

  @Test
  fun `a save point under water sends the body to a dry home`() {
    val home = Teleporting.Destination.Home().apply { savePoint = Vec3L(500, 600, 10) }
    val id = teleporting(home)
    val flooded = object : SafeGround {
      override fun isDry(x: Long, y: Long) = x != 500L
      override fun dryHome() = Vec3L(900, 900, 5)
    }

    system(safeGround = flooded).update(world, 0f)

    assertEquals(Vec3L(900, 900, 77), positionOf(id))
  }

  @Test
  fun `a save point off the grid keeps its height and leaves grounding to the sweep`() {
    val home = Teleporting.Destination.Home().apply { savePoint = Vec3L(500, 600, 10) }
    val id = teleporting(home)
    world.add(id, Grounded)

    system(ground = GroundHeight { null }).update(world, 0f)

    assertEquals(Vec3L(500, 600, 10), positionOf(id))
    assertFalse(world.has(id, Grounded::class))
  }

  @Test
  fun `a failed save point lookup leaves the body where it stood`() {
    val home = Teleporting.Destination.Home().apply { lookupFailed = true }
    val id = teleporting(home)

    system().update(world, 0f)

    assertEquals(start, positionOf(id))
    assertNull(world.get(id, Teleporting::class))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.TELEPORT_NO_LANDING)) }
  }

  private companion object {
    const val ACCOUNT_ID = 7L
  }
}

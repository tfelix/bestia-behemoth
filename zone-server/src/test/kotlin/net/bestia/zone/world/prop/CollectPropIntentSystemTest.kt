package net.bestia.zone.world.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.CollectPropIntent
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Taking a prop into the inventory with a click.
 *
 * The test that carries the design is [`two players claiming one crystal in the same tick yield exactly
 * one item`]. Everything else here is a guard rail around it.
 */
class CollectPropIntentSystemTest {

  private val propPosition = Vec3L(100, 100, 10)

  private lateinit var divergence: WorldObjectDivergenceRegistry
  private lateinit var residency: WorldObjectResidencyService
  private lateinit var out: OutMessageProcessor

  /**
   * The **real** registry, not a mock. Its synchronous in-memory write is the whole claim mechanism, so a
   * relaxed mock returning null from `of` would make every one of these tests pass for the wrong reason.
   * Only its two collaborators are stubbed, and both only carry the async DB write.
   */
  private fun newDivergence() = WorldObjectDivergenceRegistry(mockk(relaxed = true), mockk(relaxed = true))

  private fun worldService(): WorldService = mockk {
    every { record } returns mockk { every { pipelineVersion } returns 42L }
  }

  private fun newSystem(): CollectPropIntentSystem {
    divergence = newDivergence()
    residency = mockk(relaxed = true)
    out = mockk(relaxed = true)

    return CollectPropIntentSystem(
      PropKindRegistry().also { it.load() }, divergence, residency, worldService(), out
    )
  }

  /**
   * Ticks rather than calling `update` directly, so `World.iterating` is set and the adds and destroys the
   * system makes are deferred exactly as they are in production. Calling `update` straight would apply them
   * immediately and quietly hide the reason the claim has to be the divergence map and not liveness.
   */
  private fun worldWith(system: CollectPropIntentSystem): World = testWorld(systems = listOf(system))

  private fun World.addProp(
    kind: StaticEntityKind = StaticEntityKind.MANA_CRYSTAL_SMALL,
    propId: Long = 7001L
  ): EntityId = createEntity { id ->
    add(id, PropPose(propPosition, yaw = 0f))
    add(id, StaticVisual(kind, variant = 0, heightDm = 12))
    add(id, WorldObjectIdentity(propId, latticeVersion = 1L))
  }

  private fun World.addCollector(
    propEntityId: EntityId,
    accountId: Long = 1L,
    at: Vec3L = propPosition
  ): EntityId = createEntity { id ->
    add(id, Account(accountId))
    add(id, Position.fromVec3(at))
    add(id, CollectPropIntent(propEntityId))
  }

  private fun World.grantOf(collectorId: EntityId) =
    get(collectorId, ObtainItemIntent.CreateItemIntent::class)

  @Test
  fun `collecting a crystal grants its item and records a terminal depletion`() {
    val world = worldWith(newSystem())
    val prop = world.addProp()
    val collector = world.addCollector(prop)

    world.tick(0f)

    val grant = assertNotNull(world.grantOf(collector), "expected a CreateItemIntent")
    assertEquals(6L, grant.itemId)
    assertEquals(1, grant.amount)

    // Crystals have no regrow-seconds, so the divergence is terminal and the prop never comes back.
    val entry = assertNotNull(divergence.of(7001L))
    assertNull(entry.resumeAt)

    verify { residency.remove(world, prop) }
    verify(exactly = 0) { out.sendToPlayer(any(), any<OperationErrorSMSG>()) }
  }

  @Test
  fun `a large crystal yields the same item in a larger amount`() {
    val world = worldWith(newSystem())
    val collector = world.addCollector(world.addProp(kind = StaticEntityKind.MANA_CRYSTAL_LARGE))

    world.tick(0f)

    val grant = assertNotNull(world.grantOf(collector))
    assertEquals(6L, grant.itemId)
    assertEquals(2, grant.amount)
  }

  @Test
  fun `an aetherite shard yields its own item, not the crystal's`() {
    val world = worldWith(newSystem())
    val collector = world.addCollector(world.addProp(kind = StaticEntityKind.AETHERITE_SHARD_SMALL))

    world.tick(0f)

    assertEquals(7L, assertNotNull(world.grantOf(collector)).itemId)
  }

  @Test
  fun `two players claiming one crystal in the same tick yield exactly one item`() {
    val world = worldWith(newSystem())
    val prop = world.addProp()
    val first = world.addCollector(prop, accountId = 1L)
    val second = world.addCollector(prop, accountId = 2L)

    world.tick(0f)

    val grants = listOfNotNull(world.grantOf(first), world.grantOf(second))
    assertEquals(1, grants.size, "exactly one player may be granted the crystal")

    // And the loser is told why, rather than silently getting nothing.
    verify(exactly = 1) {
      out.sendToPlayer(any(), OperationErrorSMSG(OperationErrorProto.OpError.COLLECT_TARGET_GONE))
    }

    // The prop is taken out of the world once, not twice.
    verify(exactly = 1) { residency.remove(world, prop) }
  }

  @Test
  fun `liveness would not have been a safe claim`() {
    // The negative control for the test above. Both intents are resolved inside one `update`, where a
    // `world.destroy` only queues - so at the moment the second player is visited the prop is still alive
    // and still carries every component it started with. A claim built on `isAlive` or on a marker
    // component added by the first player would therefore have granted the crystal twice; only the
    // divergence map changes the instant it is written.
    val world = worldWith(newSystem())
    val prop = world.addProp()

    assertTrue(world.isAlive(prop))
  }

  @Test
  fun `a prop already recorded as depleted is refused`() {
    val world = worldWith(newSystem())
    val prop = world.addProp(propId = 7002L)
    val collector = world.addCollector(prop)

    divergence.recordDepletion(7002L, StaticEntityKind.MANA_CRYSTAL_SMALL, 42L, null)

    world.tick(0f)

    assertNull(world.grantOf(collector))
    verify { out.sendToPlayer(1L, OperationErrorSMSG(OperationErrorProto.OpError.COLLECT_TARGET_GONE)) }
    verify(exactly = 0) { residency.remove(any(), any()) }
  }

  @Test
  fun `a tree is refused because it is felled, not collected`() {
    val world = worldWith(newSystem())
    val collector = world.addCollector(world.addProp(kind = StaticEntityKind.TREE, propId = 7003L))

    world.tick(0f)

    assertNull(world.grantOf(collector))
    verify { out.sendToPlayer(1L, OperationErrorSMSG(OperationErrorProto.OpError.COLLECT_NOT_COLLECTIBLE)) }
    assertNull(divergence.of(7003L), "a refused collect must not deplete the prop")
  }

  @Test
  fun `a prop out of range is refused and left standing`() {
    val world = worldWith(newSystem())
    val prop = world.addProp(propId = 7004L)
    val collector = world.addCollector(prop, at = Vec3L(200, 200, 10))

    world.tick(0f)

    assertNull(world.grantOf(collector))
    verify { out.sendToPlayer(1L, OperationErrorSMSG(OperationErrorProto.OpError.COLLECT_OUT_OF_RANGE)) }
    assertNull(divergence.of(7004L))
    verify(exactly = 0) { residency.remove(any(), any()) }
  }

  @Test
  fun `an entity id that is not a prop is refused`() {
    val world = worldWith(newSystem())
    val notAProp = world.createEntity { }
    val collector = world.addCollector(notAProp)

    world.tick(0f)

    assertNull(world.grantOf(collector))
    verify { out.sendToPlayer(1L, OperationErrorSMSG(OperationErrorProto.OpError.COLLECT_TARGET_GONE)) }
  }

  @Test
  fun `a prop somebody already attacked is still collectible, and range still reads the pose`() {
    // Promotion adds Position and Health without removing PropPose. Range has to keep reading the pose, or
    // a crystal would become collectible only after someone had hit it.
    val world = worldWith(newSystem())
    val prop = world.addProp(propId = 7005L)
    world.add(prop, Position.fromVec3(propPosition))

    val collector = world.addCollector(prop)

    world.tick(0f)

    assertNotNull(world.grantOf(collector))
    assertNotNull(divergence.of(7005L))
  }

  @Test
  fun `the intent is always removed, so a refusal does not retry every tick`() {
    val world = worldWith(newSystem())
    val collector = world.addCollector(world.addProp(kind = StaticEntityKind.TREE, propId = 7006L))

    world.tick(0f)

    assertNull(world.get(collector, CollectPropIntent::class))
  }
}

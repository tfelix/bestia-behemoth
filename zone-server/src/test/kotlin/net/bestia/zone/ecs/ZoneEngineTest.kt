package net.bestia.zone.ecs

import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.CarryCapacityComponentSMSG
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualComponentSMSG
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.PathSMSG
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.visibility.EntityAudience
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.ecs.visibility.EntitySnapshotBuilder
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickOutbox
import net.bestia.zone.message.SMSG
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import net.bestia.zone.ecs.core.EcsWorld

/**
 * Covers what [ZoneEngine] does between ticks, rather than what any one system does:
 *
 *  - the dirty-component flush, which decides who each change is addressed to;
 *  - vanish-on-destroy ([ZoneEngine.notifyVanishOnDestroy]) - an entity that was never synced to a
 *    client needs no vanish, one that was gets broadcast to the superset of its synced components'
 *    [SyncTargets];
 *  - component removals that mean something to the client, via
 *    [net.bestia.zone.ecs.core.Removable.toRemovedMessage].
 */
class ZoneEngineTest {

  private val entityAOIService = EntityAOIService()
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val outbox = TickOutbox(mockk(relaxed = true))

  /** Records what it is told, so the position sweep's static/dynamic split can be asserted. */
  private val entityVisibility = RecordingEntityVisibility()

  /** An account that holds the chunk an entity stands in, and is therefore its audience. */
  private val watcher = 90_002L

  /** Makes [watcher] an observer of [entityId], the way holding its chunk would. */
  private fun watched(entityId: EntityId): EntityId {
    entityVisibility.observers[entityId] = setOf(watcher)

    return entityId
  }

  private lateinit var world: EcsWorld
  private lateinit var zoneEngine: ZoneEngine

  @BeforeEach
  fun setUp() {
    world = testWorld()
    zoneEngine = engineFor(world)
  }

  private fun engineFor(world: EcsWorld): ZoneEngine {
    return ZoneEngine(
      world = world,
      config = ZoneConfig(tickRate = 20),
      entityAOIService = entityAOIService,
      playerAOIService = ActivePlayerAOIService(),
      outMessageProcessor = outMessageProcessor,
      outbox = outbox,
      entityVisibility = entityVisibility,
      entityAudience = EntityAudience(entityVisibility),
      snapshotBuilder = EntitySnapshotBuilder(),
    )
  }

  @Test
  fun `destroying an entity with a publicly synced component broadcasts a vanish`() {
    val pos = Vec3L(1, 2, 0)
    val entity = world.createEntity { id ->
      add(id, Position.fromVec3(pos))
      add(id, EntityVisual(VisualKind.ITEM, 1L))
    }

    watched(entity)

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(watcher, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.GONE))
    }
  }

  @Test
  fun `destroying an entity tagged Dead broadcasts a death vanish`() {
    val pos = Vec3L(1, 2, 0)
    val entity = world.createEntity { id ->
      add(id, Position.fromVec3(pos))
      add(id, EntityVisual(VisualKind.ITEM, 1L))
      add(id, Dead())
    }

    watched(entity)

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(watcher, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.DEATH))
    }
  }

  @Test
  fun `destroying an entity with no synced component sends no vanish`() {
    // Position is itself Dirtyable (always PublicInRange), so giving the entity one would defeat the
    // point of this test - it must have no Dirtyable component at all.
    val entity = world.createEntity { }

    world.destroy(entity)

    verify(exactly = 0) { outMessageProcessor.sendToPlayer(any<Long>(), any<SMSG>()) }
  }

  @Test
  fun `a moving dynamic entity is announced to the visibility index`() {
    val entity = world.createEntity { id ->
      add(id, Position(1, 2, 0))
      add(id, EntityVisual(VisualKind.BESTIA, 1L))
    }

    zoneEngine.tickOnce(0.05f)

    assertEquals(listOf(entity to Vec3L(1, 2, 0)), entityVisibility.moves)
  }

  @Test
  fun `a static entity is kept out of the visibility index`() {
    // A promoted prop - a tree or a player-built wall that got attacked - gains a real, dirty Position while
    // still reaching clients on its chunk's static batch, so announcing it here would deliver it twice.
    world.createEntity { id ->
      add(id, Position(1, 2, 0))
      add(id, StaticSync)
    }

    zoneEngine.tickOnce(0.05f)

    assertEquals(emptyList(), entityVisibility.moves)
  }

  @Test
  fun `destroying an entity retracts it from the visibility index`() {
    val entity = world.createEntity { id -> add(id, Position(1, 2, 0)) }
    zoneEngine.tickOnce(0.05f)

    world.destroy(entity)

    assertEquals(listOf(entity), entityVisibility.forgotten)
  }

  @Test
  fun `an entity leaving a client's view is vanished as out of sight`() {
    val entity = world.createEntity { id -> add(id, Position(1, 2, 0)) }
    entityVisibility.queued = listOf(
      EntityVisibility.Delivery(accountId = watcher, appeared = emptyList(), vanished = listOf(entity))
    )

    zoneEngine.tickOnce(0.05f)

    verify(timeout = 1000) {
      // A collection, because arrivals and departures for one account go out as a single flush.
      outMessageProcessor.sendToPlayer(
        watcher,
        listOf(VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.OUT_OF_SIGHT))
      )
    }
  }

  @Test
  fun `an entity coming into view is sent once, as its snapshot`() {
    val entity = watched(world.createEntity { id ->
      add(id, Position(1, 2, 0))
      add(id, EntityVisual(VisualKind.ITEM, 1L))
    })
    entityVisibility.queued = listOf(
      EntityVisibility.Delivery(accountId = watcher, appeared = listOf(entity), vanished = emptyList())
    )

    zoneEngine.tickOnce(0.05f)

    // The snapshot goes out ordered visual first; the same components dirty from the spawn must not go too.
    verify(timeout = 1000, exactly = 1) { outMessageProcessor.sendToPlayer(watcher, any<Collection<SMSG>>()) }
    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(watcher, match<Collection<SMSG>> { it.first() is VisualComponentSMSG })
    }
  }

  @Test
  fun `removing a path tells observers the entity stopped, and where`() {
    // Seven places take a Path off an entity and only one of them is a walk finishing; combat, sleep,
    // death, a stop command and a teleport all cut one short. None dirties Position, so before the
    // removal was announced every observer walked the entity on to the end of a path it was no longer
    // following and left it there - permanently, since a stationary entity never goes dirty again.
    val pos = Vec3L(4, 5, 6)
    val entity = world.createEntity { id ->
      add(id, Position.fromVec3(pos))
      add(id, Path(mutableListOf(Vec3L(9, 9, 6))))
    }
    zoneEngine.tickOnce(0.05f)
    watched(entity)

    world.remove(entity, Path::class)
    zoneEngine.tickOnce(0.05f)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(watcher, PathSMSG(entity, emptyList(), pos))
    }
  }

  @Test
  fun `a stop reports where the entity is now, not where it was walking to`() {
    // What RespawnSystem and the GM teleport in ChunkStreamSystem both do: move the entity and drop
    // its path in the same breath. A stop position remembered from the last step walked would have
    // snapped every observer back to where the entity died.
    val entity = world.createEntity { id ->
      add(id, Position(4, 5, 6))
      add(id, Path(mutableListOf(Vec3L(9, 9, 6))))
    }
    zoneEngine.tickOnce(0.05f)
    watched(entity)

    world.get(entity, Position::class)!!.apply {
      x = 100
      y = 200
    }
    world.remove(entity, Path::class)
    zoneEngine.tickOnce(0.05f)

    val respawn = Vec3L(100, 200, 6)
    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(watcher, PathSMSG(entity, emptyList(), respawn))
    }
  }

  @Test
  fun `an unpublished step still moves the entity in the area-of-interest index`() {
    // MoveSystem steps through Position.stepTo, which does not mark the position for sync. The index
    // has to follow it anyway - it is what decides who receives a broadcast and what a skill can hit.
    val entity = world.createEntity { id -> add(id, Position(0, 0, 0)) }
    zoneEngine.tickOnce(0.05f)

    world.get(entity, Position::class)!!.stepTo(50, 50, 0)
    zoneEngine.tickOnce(0.05f)

    assertEquals(
      setOf(entity),
      entityAOIService.queryEntitiesInCube(Vec3L(50, 50, 0), 4),
      "the index must know about a step the client was not told about"
    )
  }

  @Test
  fun `destroying an entity with only an owner-only synced component notifies just the owner`() {
    val accountId = 42L
    val entity = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, CarryCapacity(current = 0, max = 100))
    }

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(accountId, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.GONE))
    }
  }

  /**
   * A position is not what makes a component deliverable. An `OwnerOnly` component is addressed to an
   * account, and an entity may legitimately have no place in the world at all - so the flush must not
   * quietly require one.
   */
  @Test
  fun `an entity with no position still syncs its owner-only components`() {
    val accountId = 43L
    val entity = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, CarryCapacity(current = 0, max = 100))
    }

    zoneEngine.tickOnce(0.05f)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(
        accountId,
        listOf(CarryCapacityComponentSMSG(entity, current = 0, max = 100))
      )
    }
  }

  @Test
  fun `a throwing system does not keep dirty components from syncing`() {
    val failing = object : System {
      override fun update(world: World, deltaTime: Float) {
        error("boom")
      }
    }
    val world = testWorld(systems = listOf(failing))
    val engine = engineFor(world)
    val accountId = 44L
    val entity = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, CarryCapacity(current = 0, max = 100))
    }

    engine.tickOnce(0.05f)

    verify(timeout = 1000) {
      outMessageProcessor.sendToPlayer(accountId, listOf(CarryCapacityComponentSMSG(entity, current = 0, max = 100)))
    }
  }

  @Test
  fun `the tick loop survives an Error thrown by a system`() {
    val runs = AtomicInteger()
    val failsOnce = object : System {
      override fun update(world: World, deltaTime: Float) {
        if (runs.incrementAndGet() == 1) TODO("not built yet")
      }
    }
    val engine = engineFor(testWorld(systems = listOf(failsOnce)))

    engine.start()
    try {
      await().atMost(Duration.ofSeconds(5)).until { runs.get() >= 3 }
    } finally {
      engine.stop()
    }
  }

  @Test
  fun `a running engine hands every system the same fixed step`() {
    val deltas = ConcurrentLinkedQueue<Float>()
    val recorder = object : System {
      override fun update(world: World, deltaTime: Float) {
        deltas.add(deltaTime)
      }
    }
    val engine = engineFor(testWorld(systems = listOf(recorder)))

    engine.start()
    try {
      await().atMost(Duration.ofSeconds(5)).until { deltas.size >= 5 }
    } finally {
      engine.stop()
    }

    assertEquals(setOf(0.05f), deltas.toSet())
  }

  @Test
  fun `an entity nothing changed on sends nothing on the next tick`() {
    val accountId = 45L
    world.createEntity { id ->
      add(id, Account(accountId))
      add(id, CarryCapacity(current = 0, max = 100))
    }
    zoneEngine.tickOnce(0.05f)
    clearMocks(outMessageProcessor, answers = false)

    zoneEngine.tickOnce(0.05f)

    verify(exactly = 0) { outMessageProcessor.sendToPlayer(any<Long>(), any<Collection<SMSG>>()) }
  }

  @Test
  fun `a component added back after a removal is sent again`() {
    val accountId = 46L
    val entity = world.createEntity { id ->
      add(id, Account(accountId))
      add(id, CarryCapacity(current = 0, max = 100))
    }
    zoneEngine.tickOnce(0.05f)

    world.remove(entity, CarryCapacity::class)
    world.add(entity, CarryCapacity(current = 7, max = 100))
    zoneEngine.tickOnce(0.05f)

    verify {
      outMessageProcessor.sendToPlayer(accountId, listOf(CarryCapacityComponentSMSG(entity, current = 7, max = 100)))
    }
  }

  @Test
  fun `an entity's position goes out ahead of its path`() {
    val pos = Vec3L(1, 2, 0)
    // Path first: the dirty log is in marking order, so this is the order a flush would otherwise send in.
    val entity = world.createEntity { id ->
      add(id, Path(mutableListOf(Vec3L(2, 2, 0))))
      add(id, Position.fromVec3(pos))
    }
    watched(entity)

    zoneEngine.tickOnce(0.05f)

    val sent = slot<Collection<SMSG>>()
    verify(timeout = 1000) { outMessageProcessor.sendToPlayer(watcher, capture(sent)) }

    assertEquals(
      listOf(PositionSMSG::class, PathSMSG::class),
      sent.captured.map { it::class },
      "entity.gd reconciles an arriving path against where it thinks the entity is"
    )
  }
}

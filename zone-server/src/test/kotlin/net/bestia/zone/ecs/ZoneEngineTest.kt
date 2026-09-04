package net.bestia.zone.ecs

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.PathSMSG
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Covers the two things [ZoneEngine] announces on its own rather than on a component's say-so:
 *
 *  - vanish-on-destroy ([ZoneEngine.notifyVanishOnDestroy]) - an entity that was never synced to a
 *    client needs no vanish, one that was gets broadcast to the superset of its synced components'
 *    [SyncTargets];
 *  - component removals that mean something to the client, via
 *    [net.bestia.zone.ecs.core.Removable.toRemovedMessage].
 */
class ZoneEngineTest {

  private val entityAOIService = EntityAOIService()
  private val playerAOIService = ActivePlayerAOIService()
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)
  private val asyncJobExecutor = AsyncJobExecutor(workerCount = 1)

  private lateinit var world: World
  private lateinit var zoneEngine: ZoneEngine

  @BeforeEach
  fun setUp() {
    world = testWorld()
    zoneEngine = ZoneEngine(
      world = world,
      config = ZoneConfig(tickRate = 20),
      entityAOIService = entityAOIService,
      playerAOIService = playerAOIService,
      outMessageProcessor = outMessageProcessor,
      asyncJobExecutor = asyncJobExecutor,
    )
  }

  @Test
  fun `destroying an entity with a publicly synced component broadcasts a vanish`() {
    val pos = Vec3L(1, 2, 0)
    val entity = world.createEntity { id ->
      add(id, Position.fromVec3(pos))
      add(id, EntityVisual(VisualKind.ITEM, 1L))
    }

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToAllPlayersInRange(pos, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.GONE))
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

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToAllPlayersInRange(pos, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.DEATH))
    }
  }

  @Test
  fun `destroying an entity with no synced component sends no vanish`() {
    // Position is itself Dirtyable (always PublicInRange), so giving the entity one would defeat the
    // point of this test - it must have no Dirtyable component at all.
    val entity = world.createEntity { }

    world.destroy(entity)

    verify(exactly = 0) { outMessageProcessor.sendToAllPlayersInRange(any<Vec3L>(), any<SMSG>()) }
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

    world.remove(entity, Path::class)
    zoneEngine.tickOnce(0.05f)

    verify(timeout = 1000) {
      outMessageProcessor.sendToAllPlayersInRange(pos, PathSMSG(entity, emptyList(), pos))
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

    world.get(entity, Position::class)!!.apply {
      x = 100
      y = 200
    }
    world.remove(entity, Path::class)
    zoneEngine.tickOnce(0.05f)

    val respawn = Vec3L(100, 200, 6)
    verify(timeout = 1000) {
      outMessageProcessor.sendToAllPlayersInRange(respawn, PathSMSG(entity, emptyList(), respawn))
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
}

package net.bestia.zone.ecs

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.ecs.item.ItemVisual
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Covers the automatic vanish-on-destroy mechanism wired into [ZoneEngine.notifyVanishOnDestroy]:
 * an entity that was never synced to a client needs no vanish, one that was gets broadcast to the
 * superset of its synced components' [SyncTargets].
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
      add(id, ItemVisual(itemId = 1L, amount = 1))
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
      add(id, ItemVisual(itemId = 1L, amount = 1))
      add(id, Dead)
    }

    world.destroy(entity)

    verify(timeout = 1000) {
      outMessageProcessor.sendToAllPlayersInRange(pos, VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.DEATH))
    }
  }

  @Test
  fun `destroying an entity with no synced component sends no vanish`() {
    val entity = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(0, 0, 0))) }

    world.destroy(entity)

    verify(exactly = 0) { outMessageProcessor.sendToAllPlayersInRange(any<Vec3L>(), any<SMSG>()) }
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

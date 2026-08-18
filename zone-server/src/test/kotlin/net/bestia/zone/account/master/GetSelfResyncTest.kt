package net.bestia.zone.account.master

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.Account
import net.bestia.zone.account.GetSelfCMSG
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.CarryCapacity
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.Optional
import kotlin.reflect.KClass

/**
 * Pins the resync half of [GetSelfHandler] against a real (system-less) [World], mocking only the
 * session lookup and the outbound processor.
 *
 * Why this matters enough to test: every component below is [Dirtyable] and pushed to the owner on
 * change, but a master spawns with its pools already full and its attributes already settled, so the
 * push at spawn is the *only* one it gets. The client is still loading its game scene at that point
 * and routinely drops it, and nothing ever re-dirties them - which left the HUD showing its scene
 * placeholder values indefinitely.
 */
class GetSelfResyncTest {

  private val accountId = 1L
  private val masterId = 7L

  private val world: World = testWorld()
  private val connectionInfoService = mockk<ConnectionInfoService>()
  private val bestiaInfoFactory = mockk<BestiaInfoFactory>()
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)

  private val masterRepository = mockk<MasterRepository>(relaxed = true).also { repo ->
    val master = Master(
      account = Account(accountId),
      name = "resync-test",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    every { repo.findById(masterId) } returns Optional.of(master)
  }

  private val handler = GetSelfHandler(
    outMessageProcessor = outMessageProcessor,
    connectionInfoService = connectionInfoService,
    bestiaInfoFactory = bestiaInfoFactory,
    masterRepository = masterRepository,
    world = world
  )

  /** Every owner-only component a selected master carries that the HUD and status window read. */
  private val resyncedComponents: List<KClass<out net.bestia.zone.ecs.core.Component>> = listOf(
    Health::class, Mana::class, Stamina::class, CarryCapacity::class, Exp::class, Level::class,
    StatusValues::class, BaseStatusValues::class, StatusPoints::class, SkillPoints::class
  )

  private fun givenSelectedMaster(): EntityId {
    val entityId = world.createEntity { id ->
      add(id, Health(current = 18, max = 18))
      add(id, Mana(current = 28, max = 28))
      add(id, Stamina(current = 27, max = 27))
      add(id, CarryCapacity(current = 0, max = 400))
      add(id, Exp(0, 100))
      add(id, Level(1))
      add(id, StatusValues(9, 9, 9, 9, 9, 9))
      add(id, BaseStatusValues(9, 9, 9, 9, 9, 9))
      add(id, StatusPoints(0))
      add(id, SkillPoints(0))
    }

    every { connectionInfoService.getMasterId(accountId) } returns masterId
    every { connectionInfoService.getSelectedMasterEntityId(accountId) } returns entityId
    every { connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId) } returns emptySet()
    every { bestiaInfoFactory.getBestiaInfo(any()) } returns emptyList()

    return entityId
  }

  private fun World.dirtyFlags(id: EntityId): Map<String, Boolean> = read {
    resyncedComponents.associate { type ->
      type.simpleName!! to (get(id, type) as Dirtyable).isDirty()
    }
  }

  private fun World.clearAllDirty(id: EntityId) = read {
    resyncedComponents.forEach { type -> (get(id, type) as Dirtyable).clearDirty() }
  }

  @Test
  fun `getting self re-dirties every owner-only component so it is pushed again`() {
    val entityId = givenSelectedMaster()
    // Simulate the state after the spawn-time push has already been flushed to a client that
    // was not listening yet.
    world.clearAllDirty(entityId)
    world.dirtyFlags(entityId).forEach { (name, dirty) -> assertFalse(dirty, "$name should start clean") }

    handler.handle(GetSelfCMSG(playerId = accountId))

    world.dirtyFlags(entityId).forEach { (name, dirty) ->
      assertTrue(dirty, "$name must be re-dirtied so the client receives it after SelfSMSG")
    }
  }

  @Test
  fun `a master entity missing a component is resynced without failing`() {
    // Player bestias and future master variants need not carry every component; the resync must
    // skip what is absent rather than throw and take the whole GetSelf response down with it.
    val entityId = world.createEntity { id -> add(id, Health(current = 5, max = 18)) }
    every { connectionInfoService.getMasterId(accountId) } returns masterId
    every { connectionInfoService.getSelectedMasterEntityId(accountId) } returns entityId
    every { connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId) } returns emptySet()
    every { bestiaInfoFactory.getBestiaInfo(any()) } returns emptyList()
    world.read { (get(entityId, Health::class) as Dirtyable).clearDirty() }

    handler.handle(GetSelfCMSG(playerId = accountId))

    assertTrue(world.read { get(entityId, Health::class)!!.isDirty() })
  }

  @Test
  fun `a stale master entity id does not fail the request`() {
    // The session could name an entity that has already been destroyed. Answering GetSelf still
    // matters more than the resync.
    every { connectionInfoService.getMasterId(accountId) } returns masterId
    every { connectionInfoService.getSelectedMasterEntityId(accountId) } returns 999L
    every { connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId) } returns emptySet()
    every { bestiaInfoFactory.getBestiaInfo(any()) } returns emptyList()

    assertTrue(handler.handle(GetSelfCMSG(playerId = accountId)))
  }
}

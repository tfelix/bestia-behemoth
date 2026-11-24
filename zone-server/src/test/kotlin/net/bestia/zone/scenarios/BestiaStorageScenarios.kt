package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestiastorage.BestiaLedgerScript
import net.bestia.zone.bestiastorage.BestiaStorageSMSG
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.bestiastorage.net.ReleaseStoredBestiaCMSG
import net.bestia.zone.bestiastorage.net.SummonStoredBestiaCMSG
import net.bestia.zone.bestiastorage.net.SwapStoredBestiaCMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.GroundItemStack
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.LootProtection
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.master.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.master.bestia.PlayerBestiaLoss
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.Teleporting
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.awt.Color
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One master with a full party and two bestia in storage, driven through the storage window's requests in order:
 * a refused summon, a swap, the rest after it, a relog, a release and a summon into a slot that came free.
 */
class BestiaStorageScenarios : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var playerBestias: PlayerBestiaRepository

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var loss: PlayerBestiaLoss

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var storage: BestiaStorageService

  @Autowired
  private lateinit var ledger: BestiaLedgerScript

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private val stored = mutableListOf<PlayerBestia>()

  /** The active bestia swapped into storage. */
  private lateinit var swappedOut: PlayerEntity

  @BeforeAll
  fun createMasterWithLedger() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "keeper",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
        slot = 0,
      )
    ).id
    inventoryService.grantToMaster(masterId, BestiaLedgerScript.ITEM_ID, 1)

    client = gameClientFactory.getGameClient(accountId = account.id)
  }

  /** Every Spring context shares one H2 database; see `PlayerBestiaRespawnScenario`. */
  @AfterAll
  fun leaveNothingBehind() {
    client.disconnect()
    bestiasInWorld().forEach { bestia -> world.modify(bestia.entityId) { id -> destroy(id) } }
    bundlesOnGround().forEach { bundle -> world.modify(bundle) { id -> destroy(id) } }
  }

  @Test
  @Order(1)
  fun `a new bestia goes into storage once every active slot is taken`() {
    repeat(ACTIVE_SLOTS) {
      assertFalse(create().isStored)
    }

    repeat(2) { stored.add(create()) }

    assertTrue(stored.all { it.isStored })
    assertEquals(ACTIVE_SLOTS, bestiasInWorld().size, "a stored bestia is not in the world")
  }

  @Test
  @Order(2)
  fun `a summon is refused while the party is full`() {
    client.connect(masterId)

    client.sendMessage(SummonStoredBestiaCMSG(client.connectedPlayerId, stored[0].id))

    awaitRefusal(OpError.BESTIA_STORAGE_PARTY_FULL)
  }

  @Test
  @Order(3)
  fun `a swap puts the stored bestia where the active one stood and drops its items in a bundle`() {
    swappedOut = bestiasInWorld().minBy { it.playerBestiaId }
    inventoryService.grantToOwner(masterId, swappedOut.playerBestiaId, APPLE, 2)
    val stoodAt = positionOf(swappedOut.entityId)

    client.sendMessage(SwapStoredBestiaCMSG(client.connectedPlayerId, stored[0].id, swappedOut.entityId))

    await {
      assertFalse(world.read { isAlive(swappedOut.entityId) })
      val cameOut = bestiasInWorld().single { it.playerBestiaId == stored[0].id }
      assertEquals(stoodAt.x to stoodAt.y, positionOf(cameOut.entityId).let { it.x to it.y })
      assertTrue(playerBestias.findByIdOrNull(swappedOut.playerBestiaId)!!.isStored)
    }
    val bundle = bundlesOnGround().single()
    assertEquals(client.connectedPlayerId, world.read { get(bundle, LootProtection::class)?.ownerAccountId })
    assertEquals(stoodAt.x to stoodAt.y, positionOf(bundle).let { it.x to it.y })
    await {
      val window = client.getLastReceived(BestiaStorageSMSG::class)
      assertTrue(window.bestias.any { it.info.playerBestiaId == swappedOut.playerBestiaId })
    }
  }

  @Test
  @Order(4)
  fun `a bestia that just went into storage rests before it comes out again`() {
    val cameOut = bestiasInWorld().single { it.playerBestiaId == stored[0].id }

    client.sendMessage(SwapStoredBestiaCMSG(client.connectedPlayerId, swappedOut.playerBestiaId, cameOut.entityId))

    awaitRefusal(OpError.BESTIA_STORAGE_RESTING)
    assertTrue(world.read { isAlive(cameOut.entityId) })
  }

  @Test
  @Order(5)
  fun `a stored bestia stays out of the world after a relog`() {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(client.connectedPlayerId)
    client.disconnect()
    await { assertFalse(world.read { isAlive(masterEntityId) }) }

    client.connect(masterId)

    await { assertEquals(ACTIVE_SLOTS, connectionInfoService.getOwnedEntitiesByMaster(client.connectedPlayerId, masterId).size) }
    assertTrue(bestiasInWorld().none { it.playerBestiaId == swappedOut.playerBestiaId })
  }

  @Test
  @Order(6)
  fun `a released bestia is gone for good`() {
    client.sendMessage(ReleaseStoredBestiaCMSG(client.connectedPlayerId, stored[1].id))

    await { assertNull(playerBestias.findByIdOrNull(stored[1].id)) }
  }

  @Test
  @Order(7)
  fun `a summon fills a free slot and lands next to the master`() {
    val lost = bestiasInWorld().first { it.playerBestiaId != stored[0].id }
    world.read { loss.lose(this, lost.entityId) }
    await { assertNull(playerBestias.findByIdOrNull(lost.playerBestiaId)) }
    restedLongEnough(swappedOut.playerBestiaId)

    client.sendMessage(SummonStoredBestiaCMSG(client.connectedPlayerId, swappedOut.playerBestiaId))

    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(client.connectedPlayerId)
    await {
      val summoned = bestiasInWorld().single { it.playerBestiaId == swappedOut.playerBestiaId }
      assertFalse(world.read { has(summoned.entityId, Teleporting::class) }, "it has landed")
      assertTrue(positionOf(summoned.entityId).distance(positionOf(masterEntityId)) <= LANDING_RADIUS)
    }
  }

  @Test
  @Order(8)
  fun `a storage center hands a ledger to a master who has none`() {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(client.connectedPlayerId)
    val ledgerId = world.read {
      getOrThrow(masterEntityId, Inventory::class).getItems().single { it.itemId == BestiaLedgerScript.ITEM_ID }.uniqueId
    }
    inventoryService.destroyInstance(masterId, ledgerId)
    world.modify(masterEntityId) { id -> getOrThrow(id, Inventory::class).removeByUniqueId(ledgerId) }
    client.clearMessages()

    storage.openAtCounter(client.connectedPlayerId)

    await {
      assertTrue(world.read { ledger.grants(this, masterEntityId) })
      assertNotNull(client.tryGetLastReceived(BestiaStorageSMSG::class))
    }
  }

  private fun create(): PlayerBestia {
    return playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))
  }

  private fun restedLongEnough(playerBestiaId: Long) {
    val row = playerBestias.findByIdOrNull(playerBestiaId)!!
    row.restsUntil = Instant.EPOCH
    playerBestias.save(row)
  }

  private fun awaitRefusal(code: OpError) {
    await { assertTrue(client.receivedAny(OperationErrorSMSG::class) { it.code == code }) }
  }

  private fun bestiasInWorld(): Set<PlayerEntity> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }
  }

  private fun bundlesOnGround(): List<EntityId> {
    return world.read {
      val found = mutableListOf<EntityId>()
      query(GroundItemStack::class).each { id ->
        if (get<GroundItemStack>().itemId == ItemBundles.ITEM_ID) found.add(id)
      }
      found
    }
  }

  private fun positionOf(entityId: EntityId): Vec3L {
    return assertNotNull(world.read { get(entityId, Position::class)?.toVec3L() })
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 48L
    const val ACTIVE_SLOTS = 4
    const val APPLE = 1L
    const val LANDING_RADIUS = 4L
  }
}

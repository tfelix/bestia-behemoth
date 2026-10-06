package net.bestia.zone.scenarios

import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterFactory
import net.bestia.zone.bestia.PlayerBestiaCreateOperation
import net.bestia.zone.bestia.PlayerBestiaCreateOperation.PlayerBestiaCreateData
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.world.MasterSpawnPointService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** A restart loses every player bestia from the world; selecting its master brings it back, exactly once. */
class PlayerBestiaRespawnScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var masterFactory: MasterFactory

  @Autowired
  private lateinit var masterSpawnPointService: MasterSpawnPointService

  @Autowired
  private lateinit var playerBestiaCreateOperation: PlayerBestiaCreateOperation

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  private lateinit var client: GameClientMock

  private var masterId = 0L

  private lateinit var respawned: PlayerEntity

  @BeforeAll
  fun createMasterWithBestia() {
    val account = accountFactory.createAccount(LOGIN_ACCOUNT_ID)
    masterId = masterFactory.create(
      account.id,
      MasterFactory.CreateMasterData(
        name = "respawner",
        hairColor = Color.BLUE,
        skinColor = Color.BLUE,
        hair = Hairstyle.HAIR_1,
        face = Face.FACE_1,
        body = BodyType.BODY_M_1,
        spawnPointId = masterSpawnPointService.ensureComputed().first().id.toInt(),
      )
    ).id
    playerBestiaCreateOperation.createAndSpawn(masterId, PlayerBestiaCreateData("blob", Vec3L.ZERO))

    client = gameClientFactory.getGameClient(accountId = account.id)
  }

  /**
   * Every Spring context shares one H2 database, and starting another context recreates its schema under this
   * world. A bestia left behind would then belong to whichever new master reuses this master's id.
   */
  @AfterAll
  fun leaveNoBestiaBehind() {
    client.disconnect()
    bestiasInWorld().forEach { bestia -> world.modify(bestia.entityId) { id -> destroy(id) } }
  }

  private fun bestiasInWorld(): Set<PlayerEntity> {
    return world.read { OwnedBestia.ownedBy(this, masterId) }
  }

  @Test
  @Order(1)
  fun `selecting the master respawns a bestia a restart lost`() {
    val lost = bestiasInWorld().single()
    world.modify(lost.entityId) { id -> destroy(id) }

    client.connect(masterId)

    respawned = bestiasInWorld().single()
    assertEquals(lost.playerBestiaId, respawned.playerBestiaId)
    assertNotEquals(lost.entityId, respawned.entityId)
    assertEquals(setOf(respawned), connectionInfoService.getOwnedEntitiesByMaster(client.connectedPlayerId, masterId))
  }

  @Test
  @Order(2)
  fun `selecting the master again keeps the bestia that is still in the world`() {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(client.connectedPlayerId)
    client.disconnect()
    await { assertFalse(world.read { isAlive(masterEntityId) }) }

    client.connect(masterId)

    assertEquals(setOf(respawned), bestiasInWorld())
    assertEquals(setOf(respawned), connectionInfoService.getOwnedEntitiesByMaster(client.connectedPlayerId, masterId))
  }

  private companion object {
    const val LOGIN_ACCOUNT_ID = 30L
  }
}

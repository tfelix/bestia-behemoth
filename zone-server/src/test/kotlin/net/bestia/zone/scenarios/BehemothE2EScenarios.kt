package net.bestia.zone.scenarios

import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.message.GetBestiasCMSG
import net.bestia.zone.system.PingCMSG
import net.bestia.zone.system.PongSMSG
import net.bestia.zone.message.entity.PositionSMSG
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.entity.MoveActiveEntityCMSG
import net.bestia.zone.geometry.Vec3L
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

/**
 * This does test very basics like a minimal server start and the basic connection logic
 * of a new client.
 */
class BehemothE2EScenarios : BestiaNoSocketScenario(
  autoClientConnect = false
) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var playerBestiaRepository: PlayerBestiaRepository

  @Test
  @Order(1)
  fun `before connection only owned player bestia are active`() {
    val masterId1 = testData.account1.masterIds[0]
    val ownedEntities1 = connectionInfoService.getOwnedEntitiesByMaster(clientPlayer1.connectedPlayerId, masterId1)

    assertEquals(2, ownedEntities1.size)

    val masterId2 = testData.account2.masterIds[0]
    val ownedEntities2 = connectionInfoService.getOwnedEntitiesByMaster(clientPlayer2.connectedPlayerId, masterId2)
    assertTrue(ownedEntities2.isEmpty())
  }

  @Test
  @Order(2)
  fun `before connection no master is active`() {
    assertThrows<IllegalArgumentException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    }
    assertThrows<IllegalArgumentException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)
    }
    assertThrows<IllegalArgumentException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer3.connectedPlayerId)
    }
  }

  @Test
  @Order(4)
  fun `connecting and requesting a list of current bestias work`() {
    clientPlayer1.connect()

    val expectedPlayerBestiaIds = playerBestiaRepository.findAllByMasterId(testData.account1.masterIds.first())
      .map { it.id }
    val expectedMasterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    clientPlayer1.sendMessage(GetBestiasCMSG(clientPlayer1.connectedPlayerId))

    // TODO this must be replaced with the GetSelf message
    /*
    await {
      val availableBestias = clientPlayer1.tryGetLastReceived(AvailableBestias::class)

      assertNotNull(availableBestias)

      val receivedPlayerBestiaIds = availableBestias!!.bestias.map { it.playerBestiaId }
      assertEquals(expectedPlayerBestiaIds, receivedPlayerBestiaIds)

      assertEquals(expectedMasterEntityId, availableBestias.masterEntityId)
    }*/
  }

  /* TODO we need an entity which moves
  @Test
  @Order(5)
  fun `connecting and receiving entity data when connected works`() {
    Awaitility.await().untilAsserted {
      val pos = clientPlayer1.tryGetLastReceived(PositionMessage::class)
      assertNotNull(pos)
    }
  }*/

  @Test
  @Order(6)
  fun `moving around works and position updates are received`() {
    val msg = MoveActiveEntityCMSG(
      playerId = clientPlayer1.connectedPlayerId,
      path = listOf(
        Vec3L(0, 1, 0),
        Vec3L(0, 2, 0),
        Vec3L(0, 3, 0)
      )
    )

    clientPlayer1.sendMessage(msg)

    // check DB + received packages
    Awaitility.await().untilAsserted {
      val pos = clientPlayer1.tryGetLastReceived(PositionSMSG::class)
      assertNotNull(pos)
    }
  }

  @Test
  @Order(7)
  fun `tx ping rx pong`() {
    clientPlayer1.sendMessage(PingCMSG(clientPlayer1.connectedPlayerId))

    val pong = clientPlayer1.tryGetLastReceived(PongSMSG::class)

    assertNotNull(pong)
  }

  @Test
  @Order(8)
  fun `disconnecting leaves the zone server in a clean and defined state`() {
    clientPlayer1.disconnect()

    // master entity was despawned?

    // all player bestia still active as entity?

    // position of master entity was updated into the DB?

    // is PlayerAOIService cleaned again?

    // is socket registry clean again?

    // is master Entity removed from shard registry?

    // is maste entity removed from AOI service for masters?

    // is master enttiy removed from general entity aoi service?
  }
}

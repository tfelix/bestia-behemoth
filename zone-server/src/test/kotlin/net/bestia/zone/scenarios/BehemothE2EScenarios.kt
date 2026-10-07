package net.bestia.zone.scenarios

import net.bestia.zone.master.net.GetSelfCMSG
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.socket.net.PingCMSG
import net.bestia.zone.socket.PongSMSG
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.PathSMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.control.net.MoveActiveEntityCMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
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

  @Autowired
  private lateinit var world: WorldView

  /**
   * Asks the world rather than the session map: a disconnect drops the session, and every scenario sharing this
   * context connects and disconnects these players, but their bestias stay in the world throughout.
   */
  @Test
  @Order(1)
  fun `before connection only owned player bestia are in the world`() {
    val bestiasOfPlayer1 = world.read { OwnedBestia.ownedBy(this, testData.account1.masterIds[0]) }
    assertEquals(2, bestiasOfPlayer1.size)

    val bestiasOfPlayer2 = world.read { OwnedBestia.ownedBy(this, testData.account2.masterIds[0]) }
    assertTrue(bestiasOfPlayer2.isEmpty())
  }

  @Test
  @Order(2)
  fun `before connection no master is active`() {
    assertThrows<NoActiveSessionException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    }
    assertThrows<NoActiveSessionException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)
    }
    assertThrows<NoActiveSessionException> {
      connectionInfoService.getSelectedMasterEntityId(clientPlayer3.connectedPlayerId)
    }
  }

  @Test
  @Order(4)
  fun `connecting and requesting a list of current bestias work`() {
    clientPlayer1.connect(testData.account1.masterIds.first())

    val expectedPlayerBestiaIds = playerBestiaRepository.findAllByMasterId(testData.account1.masterIds.first())
      .map { it.id }
    val expectedMasterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    clientPlayer1.sendMessage(GetSelfCMSG(clientPlayer1.connectedPlayerId))

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
    // Relative to wherever the master actually is, rather than to the origin. Masters used to start at
    // Vec3L.ZERO and now start in the middle of the map, so an absolute path was a ninety-kilometre walk that
    // never produced an update. What this test is about is that a path is accepted and echoed back, and that
    // holds wherever the player stands.
    val entityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    val from = world.read { get(entityId, Position::class)!!.toVec3L() }

    val msg = MoveActiveEntityCMSG(
      playerId = clientPlayer1.connectedPlayerId,
      path = listOf(
        Vec3L(from.x, from.y + 1, from.z),
        Vec3L(from.x, from.y + 2, from.z),
        Vec3L(from.x, from.y + 3, from.z)
      )
    )

    clientPlayer1.sendMessage(msg)

    // Asserted against this entity and against the tile it stopped on. A bare "some PositionSMSG arrived"
    // passed whether or not the walk was accepted, because every nearby entity's traffic lands in the same
    // mailbox - and a three-tile walk publishes no position of its own at all: MoveSystem sends one every
    // POSITION_RESYNC_STEPS tiles and the arrival rides the stop notification instead.
    Awaitility.await().untilAsserted {
      val arrived = world.read { get(entityId, Position::class)!!.toVec3L() }
      assertEquals(from.y + 3, arrived.y, "the walk did not move the entity")

      assertTrue(
        clientPlayer1.receivedAny(PathSMSG::class) {
          it.entityId == entityId && it.path.isEmpty() && it.stopPosition?.y == from.y + 3
        },
        "a player must be told where its own entity ended up"
      )
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

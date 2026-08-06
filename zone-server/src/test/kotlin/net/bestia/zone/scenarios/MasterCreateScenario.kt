package net.bestia.zone.scenarios

import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterErrorSMSG
import net.bestia.zone.extensions.test
import net.bestia.zone.account.master.AvailableMasterSMSG
import net.bestia.zone.account.master.CreateMasterCMSG
import net.bestia.zone.account.master.MasterCreatedSMSG
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.dialog.DialogSMSG
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import net.bestia.zone.world.MasterSpawnPointRepository
import net.bestia.zone.world.findByIdOrThrow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scenario: Spawns a blob bestia entity, connects, sends a kill message, and provides a placeholder for post-kill checks.
 */
class MasterCreateScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var masterSpawnPointRepository: MasterSpawnPointRepository

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: World

  private lateinit var accountNoMaster: Account

  private lateinit var clientPlayerNoMaster: GameClientMock

  private var listedMasterId: Long = 0

  @BeforeAll
  fun setupMaster() {
    accountNoMaster = accountFactory.createAccount(4L)

    clientPlayerNoMaster = gameClientFactory.getGameClient(
      accountId = accountNoMaster.id,
    )
  }

  @AfterAll
  fun teardownMaster() {
    clientPlayerNoMaster.disconnect()
  }

  @BeforeEach
  fun beforeClearMessages() {
    clientPlayerNoMaster.clearMessages()
  }

  @Test
  @Order(1)
  fun `listing master for a new account returns an empty list`() {
    clientPlayerNoMaster.sendMessage(GetMasterCMSG(clientPlayerNoMaster.connectedPlayerId))

    await {
      val masterList = clientPlayerNoMaster.getLastReceived(AvailableMasterSMSG::class)

      assertEquals(0, masterList.master.size)
    }
  }

  @Test
  @Order(2)
  fun `creating a master works`() {
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r")
    )

    await {
      // Creation is acknowledged with a success message only; the created master's data is
      // verified via an explicit GetMaster in the next test (mirroring the real client flow).
      clientPlayerNoMaster.getLastReceived(MasterCreatedSMSG::class)
    }
  }

  @Test
  @Order(3)
  fun `listing master again returns the newly created master`() {
    clientPlayerNoMaster.sendMessage(GetMasterCMSG(clientPlayerNoMaster.connectedPlayerId))

    await {
      val masterList = clientPlayerNoMaster.getLastReceived(AvailableMasterSMSG::class)

      assertEquals(1, masterList.master.size)

      val master = masterList.master.first()
      assertEquals("mast0r", master.name)
      assertEquals(Color.BLUE, master.hairColor)
      assertEquals(Color.BLUE, master.skinColor)
      assertEquals(Hairstyle.HAIR_1, master.hair)
      assertEquals(Face.FACE_1, master.face)
      assertEquals(BodyType.BODY_M_1, master.body)

      listedMasterId = master.id
    }
  }

  @Test
  @Order(4)
  fun `selecting the newly created master materializes it carrying the intro marker effect`() {
    clientPlayerNoMaster.sendMessage(
      SelectMasterCMSG(
        clientPlayerNoMaster.connectedPlayerId,
        listedMasterId
      )
    )

    await {
      val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayerNoMaster.connectedPlayerId)
      val statusEffects = world.get(masterEntityId, StatusEffects::class)

      assertNotNull(statusEffects, "a materialized master must carry StatusEffects")

      val marker = statusEffects.activeEffects
        .firstOrNull { it.definitionId == StatusEffectId.MASTER_INTRO_MARKER.id }

      assertNotNull(marker, "MasterEntityFactory must attach the MASTER_INTRO_MARKER effect")
      assertFalse(marker.isSyncedToClient, "the marker is server-internal and must never reach the client")
    }

    // The marker is a placeholder hook only: it is deliberately inert, so selecting a master must
    // not produce a dialog yet. This is what will change once the intro trigger is built on it.
    assertNull(
      clientPlayerNoMaster.tryGetLastReceived(DialogSMSG::class),
      "the intro marker does not send a dialog yet"
    )
  }

  @Test
  @Order(5)
  fun `creating a master with an invalid name fails`() {
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r".repeat(10))
    )

    await {
      val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

      assertEquals(MasterErrorSMSG.MasterErrorCode.INVALID_NAME, masterErrorSMSG.error)
    }
  }

  @Test
  @Order(5)
  fun `creating a master with same name fails`() {
    clientPlayerNoMaster.sendMessage(
      CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r")
    )

    await {
      val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

      assertEquals(MasterErrorSMSG.MasterErrorCode.NAME_ALREADY_TAKEN, masterErrorSMSG.error)
    }
  }

  @Test
  @Order(6)
  fun `creating more than the allowed master fails`() {
    // we have already created one master with this we effectively create maxMasters + 1
    (1..Account.DEFAULT_MASTER_SLOT_COUNT).forEach { i ->
      clientPlayerNoMaster.sendMessage(
        CreateMasterCMSG.test(clientPlayerNoMaster.connectedPlayerId, "mast0r-number-$i")
      )

      if (i < Account.DEFAULT_MASTER_SLOT_COUNT) {
        val masterErrorSMSG = clientPlayerNoMaster.tryGetLastReceived(MasterErrorSMSG::class)
        assertNull(masterErrorSMSG)
      }

      if (i == Account.DEFAULT_MASTER_SLOT_COUNT) {
        await {
          val masterErrorSMSG = clientPlayerNoMaster.getLastReceived(MasterErrorSMSG::class)

          assertEquals(MasterErrorSMSG.MasterErrorCode.MAX_MASTERS_REACHED, masterErrorSMSG.error)
        }
      }
    }
  }

  @Test
  @Order(7)
  fun `listing master also returns spawn point candidates ranked below the largest settlement`() {
    val account = accountFactory.createAccount(5L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(GetMasterCMSG(client.connectedPlayerId))

      await {
        val response = client.getLastReceived(AvailableMasterSMSG::class)

        assertTrue(response.spawnPoints.isNotEmpty(), "no spawn point candidates were offered")
        assertTrue(response.spawnPoints.size <= SettlementSpawnPoints.MAX_HOME_CANDIDATES)
        response.spawnPoints.forEach { candidate ->
          assertTrue(candidate.settlementName.isNotBlank())
          assertTrue(candidate.tier.isNotBlank())
        }
      }
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(8)
  fun `creating a master with a chosen spawn point sets its position and home settlement`() {
    val account = accountFactory.createAccount(6L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(GetMasterCMSG(client.connectedPlayerId))

      var chosen: AvailableMasterSMSG.SpawnPointCandidate? = null
      await {
        val response = client.getLastReceived(AvailableMasterSMSG::class)
        assertTrue(response.spawnPoints.isNotEmpty())
        chosen = response.spawnPoints.first()
      }
      val candidate = chosen!!

      client.clearMessages()
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "spawnpicker", spawnPointId = candidate.id)
      )

      await {
        client.getLastReceived(MasterCreatedSMSG::class)
      }

      val master = masterRepository.findByName("spawnpicker")!!
      val spawnPoint = masterSpawnPointRepository.findByIdOrThrow(candidate.id.toLong())

      assertEquals(candidate.settlementName, master.homeSettlementName)
      assertEquals(spawnPoint.position, master.spawnPosition)
      assertEquals(spawnPoint.position, master.currentPosition)
    } finally {
      client.disconnect()
    }
  }

  @Test
  @Order(9)
  fun `creating a master with an invalid spawn point id fails`() {
    val account = accountFactory.createAccount(7L)
    val client = gameClientFactory.getGameClient(accountId = account.id)

    try {
      client.sendMessage(
        CreateMasterCMSG.test(client.connectedPlayerId, "badspawn", spawnPointId = Int.MAX_VALUE)
      )

      await {
        val masterErrorSMSG = client.getLastReceived(MasterErrorSMSG::class)

        assertEquals(MasterErrorSMSG.MasterErrorCode.INVALID_SPAWN_POINT, masterErrorSMSG.error)
      }
    } finally {
      client.disconnect()
    }
  }
}





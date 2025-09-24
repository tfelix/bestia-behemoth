package net.bestia.zone.scenarios

import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountFactory
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.MasterErrorSMSG
import net.bestia.zone.extensions.test
import net.bestia.zone.message.AvailableMasterSMSG
import net.bestia.zone.message.CMSG
import net.bestia.zone.message.CreateMasterCMSG
import net.bestia.zone.message.GetMasterCMSG
import net.bestia.zone.message.SelectMasterCMSG
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.mocks.GameClientMockFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.awt.Color
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Scenario: Spawns a blob bestia entity, connects, sends a kill message, and provides a placeholder for post-kill checks.
 */
class MasterCreateScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var accountFactory: AccountFactory

  @Autowired
  private lateinit var gameClientFactory: GameClientMockFactory

  private lateinit var accountNoMaster: Account

  private lateinit var clientPlayerNoMaster: GameClientMock

  private var listedMasterId: Long = 0

  @BeforeAll
  fun setupMaster() {
    accountNoMaster = accountFactory.createAccount(1L)

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
      val masterList = clientPlayerNoMaster.getLastReceived(AvailableMasterSMSG::class)

      assertEquals(1, masterList.master.size)

      val master = masterList.master.first()
      assertEquals("mast0r", master.name)
      assertEquals(Color.BLUE, master.hairColor)
      assertEquals(Color.BLUE, master.skinColor)
      assertEquals(Hairstyle.HAIR_1, master.hair)
      assertEquals(Face.FACE_1, master.face)
      assertEquals(BodyType.BODY_M_1, master.body)
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
  fun `selecting the newly created master works`() {
    clientPlayerNoMaster.sendMessage(
      SelectMasterCMSG(
        clientPlayerNoMaster.connectedPlayerId,
        listedMasterId
      )
    )

    // TODO maybe somehow validate this?
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
}





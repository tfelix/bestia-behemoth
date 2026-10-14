package net.bestia.zone.scenarios

import net.bestia.zone.master.net.GetSelfCMSG
import net.bestia.zone.control.net.SelectEntityCMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.control.net.MoveActiveEntityCMSG
import net.bestia.zone.message.SelfSMSG
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ListAvailableBestiaScenarios : BestiaNoSocketScenario() {

  private var availablePlayerBestiaEntityForSelection: Long = 0

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Test
  fun `getting self lists the owned bestias in slot order with the slot count`() {
    val accountId = clientPlayer1.connectedPlayerId
    val owned = connectionInfoService.getOwnedEntitiesByMaster(accountId, connectionInfoService.getMasterId(accountId))
    val playerBestiaIdByEntity = owned.associate { it.entityId to it.playerBestiaId }

    clientPlayer1.clearMessages()
    clientPlayer1.sendMessage(GetSelfCMSG(accountId))

    await {
      val self = assertNotNull(clientPlayer1.tryGetLastReceived(SelfSMSG::class))

      assertEquals(4, self.bestiaSlotCount, "the configured base slots, the account has bought none")
      val listedPlayerBestiaIds = self.availableBestias.map { playerBestiaIdByEntity.getValue(it.entityId) }
      assertEquals(2, listedPlayerBestiaIds.size)
      assertEquals(listedPlayerBestiaIds.sorted(), listedPlayerBestiaIds)
    }
  }

  @Test
  @Disabled("listAvailableBestias() assertions are commented out pending a replacement GetSelf message - test never verified anything")
  fun `available bestias can be listed and selected`() {
    listAvailableBestias()
    selectingBestiaWorks()
    // assertSelectedBestiaReceivesUpdatesAboutCloseEntities()
    movingSelectedBestiaWorks()
    // assertSelectedBestiaMoved()
    // switchActiveBestia()
    // assertUpdatesOfPreviousBestiaStops()
    // assertNewBestiaReceivesUpdatesAboutCloseEntities()
  }

  private fun listAvailableBestias() {
    clientPlayer1.sendMessage(GetSelfCMSG(clientPlayer1.connectedPlayerId))

    // TODO this must be replaced with the getself message
    /*
    await {
      val msg = clientPlayer1.tryGetLastReceived(AvailableBestias::class)

      assertNotNull(msg)
      assertEquals(4, msg.maxAvailableSlots)
      assertEquals("blob", msg.bestias[0].modelIdentifier)
      assertEquals("blob", msg.bestias[0].modelIdentifier)

      availablePlayerBestiaEntityForSelection = msg.bestias.first().playerBestiaEntityId
    }*/
  }

  fun selectingBestiaWorks() {
    val initialActiveEntityId = connectionInfoService.getActiveEntityId(testData.account1.account.id)

    clientPlayer1.sendMessage(SelectEntityCMSG(clientPlayer1.connectedPlayerId, availablePlayerBestiaEntityForSelection))

    assertNotEquals(
      initialActiveEntityId,
      connectionInfoService.getActiveEntityId(testData.account1.account.id)
    )
  }

  private fun assertSelectedBestiaReceivesUpdatesAboutCloseEntities() {
    // TODO()
  }

  private fun movingSelectedBestiaWorks() {
    clientPlayer1.sendMessage(
      MoveActiveEntityCMSG(
        playerId = testData.account1.account.id,
        path = emptyList()
      )
    )
  }
}



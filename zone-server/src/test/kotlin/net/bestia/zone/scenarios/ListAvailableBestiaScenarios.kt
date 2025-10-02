package net.bestia.zone.scenarios

import net.bestia.zone.message.GetBestiasCMSG
import net.bestia.zone.message.SelectEntityCMSG
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.entity.MoveActiveEntityCMSG
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNotEquals

class ListAvailableBestiaScenarios : BestiaNoSocketScenario() {

  private var availablePlayerBestiaEntityForSelection: Long = 0

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Test
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
    clientPlayer1.sendMessage(GetBestiasCMSG(clientPlayer1.connectedPlayerId))

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



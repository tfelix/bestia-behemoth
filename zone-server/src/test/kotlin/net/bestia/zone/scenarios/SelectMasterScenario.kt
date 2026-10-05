package net.bestia.zone.scenarios

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

/**
 * A master is only ever selected by the account that owns it, and only into a session that has none yet.
 */
class SelectMasterScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var world: WorldView

  @Test
  @Order(1)
  fun `another account's master cannot be selected`() {
    val foreignMasterId = testData.account1.masterIds.first()
    val foreignEntityId = masterRepository.findByIdOrThrow(foreignMasterId).entityId

    clientPlayer2.connect()
    clientPlayer2.sendMessage(SelectMasterCMSG(clientPlayer2.connectedPlayerId, foreignMasterId))

    assertFalse(world.isAlive(foreignEntityId), "a foreign master must not be spawned")
    assertThrows<NoActiveSessionException> {
      connectionInfoService.getMasterId(clientPlayer1.connectedPlayerId)
    }
  }
}

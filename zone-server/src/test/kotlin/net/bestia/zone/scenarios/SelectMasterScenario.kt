package net.bestia.zone.scenarios

import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.SelectMasterCMSG
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import net.bestia.zone.ecs.logout.DisconnectProtection
import net.bestia.zone.ecs.logout.LogoutIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * A master is only ever selected by the account that owns it, and only into a session that has none yet.
 *
 * The logout window is short because an earlier scenario can leave account 1's master protected in the world.
 */
@TestPropertySource(properties = ["world.logout-protection-seconds=1.5"])
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
    await { assertFalse(world.isAlive(foreignEntityId), "left over from an earlier scenario") }

    clientPlayer2.connect()
    clientPlayer2.sendMessage(SelectMasterCMSG(clientPlayer2.connectedPlayerId, foreignMasterId))

    assertFalse(world.isAlive(foreignEntityId), "a foreign master must not be spawned")
    assertThrows<NoActiveSessionException> {
      connectionInfoService.getMasterId(clientPlayer1.connectedPlayerId)
    }
  }

  @Test
  @Order(2)
  fun `a second master cannot be selected while one is active`() {
    val activeMasterId = testData.account1.masterIds[0]
    val otherMasterId = testData.account1.masterIds[1]
    val otherEntityId = masterRepository.findByIdOrThrow(otherMasterId).entityId

    clientPlayer1.connect(activeMasterId)
    clientPlayer1.sendMessage(SelectMasterCMSG(clientPlayer1.connectedPlayerId, otherMasterId))

    assertFalse(world.isAlive(otherEntityId), "a second master must not be spawned next to the active one")
    assertEquals(activeMasterId, connectionInfoService.getMasterId(clientPlayer1.connectedPlayerId))
  }

  /**
   * A reload from the database would roll the master back to its last save and revive it if it died since,
   * so a master that is still in the world is picked up again as it is.
   */
  @Test
  @Order(3)
  fun `re-selecting a master that is still in the world re-attaches to it`() {
    val masterId = testData.account3.masterIds.first()
    val accountId = clientPlayer3.connectedPlayerId

    clientPlayer3.connect(masterId)
    val entityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    world.modify(entityId) { id ->
      add(id, NeverPersisted)
      add(id, LogoutIntent())
      add(id, DisconnectProtection(remainingSeconds = 20f))
    }
    connectionInfoService.deactivateSession(accountId)

    clientPlayer3.sendMessage(SelectMasterCMSG(accountId, masterId))

    assertTrue(world.has(entityId, NeverPersisted::class), "the live entity must be kept, not reloaded")
    assertFalse(world.has(entityId, LogoutIntent::class), "picking the master back up ends its logout")
    assertFalse(world.has(entityId, DisconnectProtection::class), "and its disconnect protection")
    assertEquals(entityId, connectionInfoService.getSelectedMasterEntityId(accountId))
  }

  private object NeverPersisted : Component
}

package net.bestia.zone.scenarios

import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import net.bestia.zone.ecs.logout.LogoutIntentComponentSMSG
import net.bestia.zone.ecs.logout.RequestLogoutCMSG
import net.bestia.zone.entity.MoveActiveEntityCMSG
import net.bestia.zone.entity.VanishEntitySMSG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

/**
 * Exercises the delayed-logout flow end-to-end through the ECS: requesting a logout, the countdown
 * elapsing into a despawn (which the client sees as a vanish), and cancellation by player activity.
 *
 * The logout window is shortened to keep the test fast; the tick loop is real, so timing is genuine.
 */
@TestPropertySource(properties = ["world.logout-protection-seconds=1.5"])
class LogoutScenario : BestiaNoSocketScenario(autoClientConnect = false) {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Test
  @Order(1)
  fun `requesting logout syncs a countdown then despawns the master and deactivates the session`() {
    clientPlayer1.connect(testData.account1.masterIds.first())
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(RequestLogoutCMSG(clientPlayer1.connectedPlayerId))

    // The pending countdown is synced to the owner.
    await {
      val countdown = clientPlayer1.tryGetLastReceived(LogoutIntentComponentSMSG::class)
      assertEquals(masterEntityId, countdown?.entityId)
    }

    // Once it elapses the master vanishes for its owner...
    await {
      val vanish = clientPlayer1.receivedAny(VanishEntitySMSG::class) { it.entityId == masterEntityId }
      assert(vanish) { "expected the master entity to vanish for its owner" }
    }

    // ...and the session is deactivated so the player is back in master-select limbo.
    await {
      assertThrows<NoActiveSessionException> {
        connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
      }
    }
  }

  @Test
  @Order(2)
  fun `player activity cancels a pending logout`() {
    clientPlayer2.connect(testData.account2.masterIds.first())
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId)
    clientPlayer2.clearMessages()

    clientPlayer2.sendMessage(RequestLogoutCMSG(clientPlayer2.connectedPlayerId))
    await {
      assertEquals(masterEntityId, clientPlayer2.tryGetLastReceived(LogoutIntentComponentSMSG::class)?.entityId)
    }

    // An empty-path move ("stop where I stand") is exactly what the client's Cancel button sends.
    clientPlayer2.clearMessages()
    clientPlayer2.sendMessage(MoveActiveEntityCMSG(clientPlayer2.connectedPlayerId, emptyList()))

    // Cancellation is signalled by a LogoutIntentComponentSMSG with removed = true.
    await {
      val removed = clientPlayer2.tryGetLastReceived(LogoutIntentComponentSMSG::class)
      assertEquals(true, removed?.removed)
      assertEquals(masterEntityId, removed?.entityId)
    }

    // Well past the (shortened) window: the master must NOT have despawned and the session is intact.
    Thread.sleep(2000)
    assertNull(
      clientPlayer2.tryGetLastReceived(VanishEntitySMSG::class),
      "a cancelled logout must not despawn the master"
    )
    assertEquals(masterEntityId, connectionInfoService.getSelectedMasterEntityId(clientPlayer2.connectedPlayerId))
  }
}

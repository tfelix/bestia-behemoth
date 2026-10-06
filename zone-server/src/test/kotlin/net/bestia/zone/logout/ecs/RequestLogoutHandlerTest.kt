package net.bestia.zone.logout.ecs

import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Logging out removes the master from the world, and the countdown is what keeps that from being an escape from a
 * fight. On a controlled bestia instead, the countdown would end with the master still standing.
 */
class RequestLogoutHandlerTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()

  @Test
  fun `a logout while controlling a bestia counts down on the master`() {
    val master = world.createEntity { }
    val bestia = world.createEntity { }
    sessions.activateSession(ACCOUNT, MASTER_ID, master)
    sessions.registerPlayerBestiaEntity(ACCOUNT, MASTER_ID, playerBestiaId = 5L, playerBestiaEntityId = bestia)
    sessions.activateEntity(ACCOUNT, bestia)

    RequestLogoutHandler(sessions, WorldRulesConfig(tickRate = 20)).handle(world, RequestLogoutCMSG(ACCOUNT))

    assertTrue(world.has(master, LogoutIntent::class))
    assertFalse(world.has(bestia, LogoutIntent::class))
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER_ID = 2L
  }
}

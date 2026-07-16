package net.bestia.zone.ecs.logout

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Starts a logout countdown on the player's currently active master by attaching a [LogoutIntent].
 * Idempotent: re-requesting while one is already pending leaves the running countdown untouched.
 */
@Component
class RequestLogoutHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val zoneConfig: ZoneConfig,
) : InMessageProcessor.IncomingMessageHandler<RequestLogoutCMSG> {
  override val handles = RequestLogoutCMSG::class

  override fun handle(msg: RequestLogoutCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    world.modify(activeEntityId) { id ->
      if (get(id, LogoutIntent::class) == null) {
        add(id, LogoutIntent(zoneConfig.logoutProtectionSeconds))
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

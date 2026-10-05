package net.bestia.zone.ecs.logout

import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Starts a logout countdown on the player's selected master by attaching a [LogoutIntent].
 * Idempotent: re-requesting while one is already pending leaves the running countdown untouched.
 *
 * The master even while the player controls a bestia: the master is what leaves the world, so it is what the
 * countdown has to hold in place.
 */
@Component
class RequestLogoutHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val zoneConfig: ZoneConfig,
) : InMessageProcessor.IncomingMessageHandler<RequestLogoutCMSG> {
  override val handles = RequestLogoutCMSG::class

  override fun handle(msg: RequestLogoutCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    world.modify(masterEntityId) { id ->
      if (get(id, LogoutIntent::class) == null) {
        add(id, LogoutIntent(zoneConfig.logoutProtectionSeconds))
      }
    }

    return true
  }
}

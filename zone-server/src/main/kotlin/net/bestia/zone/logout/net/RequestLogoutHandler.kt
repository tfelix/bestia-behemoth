package net.bestia.zone.logout.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.logout.ecs.LogoutIntent

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
  private val zoneConfig: WorldRulesConfig,
) : TickMessageHandler<RequestLogoutCMSG> {
  override val wire = decoder(MessageCase.REQUEST_LOGOUT) { accountId, envelope ->
    RequestLogoutCMSG.fromBnet(accountId, envelope.requestLogout)
  }

  override fun handle(world: World, msg: RequestLogoutCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    world.modify(masterEntityId) { id ->
      if (get(id, LogoutIntent::class) == null) {
        add(id, LogoutIntent(zoneConfig.logoutProtectionSeconds))
      }
    }

    return true
  }
}

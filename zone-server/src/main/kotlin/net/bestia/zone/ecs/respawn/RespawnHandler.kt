package net.bestia.zone.ecs.respawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Revives the player's currently active entity at its save point.
 *
 * The save point is resolved here, on the message thread, and handed to [RespawnSystem] inside the
 * [Respawn] intent - the tick thread must not go to the database for it.
 */
@Component
class RespawnHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val savePointService: SavePointService,
  private val world: WorldView,
) : InMessageProcessor.IncomingMessageHandler<RespawnCMSG> {
  override val handles = RespawnCMSG::class

  override fun handle(msg: RespawnCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // No error code: an honest client only offers the button while the death window is up, so this
    // is a client bug or a hand-crafted packet, not something a player is meant to read about.
    if (!world.has(activeEntityId, Dead::class)) {
      LOG.warn { "Account ${msg.playerId} asked to respawn entity $activeEntityId, which is not dead" }

      return true
    }

    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)
    val savePoint = if (playerBestiaId == null) {
      savePointService.forMaster(connectionInfoService.getMasterId(msg.playerId))
    } else {
      savePointService.forPlayerBestia(playerBestiaId)
    }

    world.modify(activeEntityId) { id ->
      add(id, Respawn(savePoint))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

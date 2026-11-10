package net.bestia.zone.respawn.net

import net.bestia.zone.casting.camp.BaseCamps
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.account.SavePointService
import net.bestia.zone.respawn.ecs.Respawn

/**
 * Revives the player's currently active entity at its save point.
 *
 * The save point is resolved here, on the IO lane, and handed to [net.bestia.zone.respawn.ecs.RespawnSystem] inside the
 * [Respawn] intent - the tick thread must not go to the database for it.
 */
@Component
class RespawnHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val savePointService: SavePointService,
  private val world: WorldView,
  private val camps: BaseCamps? = null,
) : IoMessageHandler<RespawnCMSG> {
  override val wire = decoder(MessageCase.RESPAWN) { accountId, envelope ->
    RespawnCMSG.fromBnet(accountId, envelope.respawn)
  }

  override fun handle(msg: RespawnCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // No error code: an honest client only offers the button while the death window is up, so this
    // is a client bug or a hand-crafted packet, not something a player is meant to read about.
    if (!world.read { has(activeEntityId, Dead::class) }) {
      LOG.warn { "Account ${msg.playerId} asked to respawn entity $activeEntityId, which is not dead" }

      return true
    }

    val playerBestiaId = connectionInfoService.getActivePlayerBestiaId(msg.playerId)
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    // A party's Lv.3 Base Camp takes its fallen masters back instead of their save point.
    val camp = if (playerBestiaId == null) {
      world.read { camps?.respawnPointFor(this, activeEntityId, masterId) }
    } else {
      null
    }
    val savePoint = camp ?: if (playerBestiaId == null) {
      savePointService.forMaster(masterId)
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

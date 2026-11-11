package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.master.bestia.NamedPlaceService
import net.bestia.zone.master.bestia.OrderBook
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Names a work area around where one of the player's bestias stands. */
@Component
class SaveWorkAreaHandler(
  private val sessions: ConnectionInfoService,
  private val places: NamedPlaceService,
  private val world: WorldView,
  private val book: OrderBook,
  private val out: OutMessageProcessor,
) : IoMessageHandler<SaveWorkAreaCMSG> {

  override val wire = decoder(MessageCase.SAVE_WORK_AREA) { accountId, envelope ->
    val msg = envelope.saveWorkArea
    SaveWorkAreaCMSG(accountId, msg.playerBestiaId, msg.name, msg.radius.toLong())
  }

  override fun handle(msg: SaveWorkAreaCMSG): Boolean {
    val masterId = sessions.activeMasterIdOf(msg.playerId) ?: return false
    val bestia = sessions.ownedBestiaOf(msg.playerId, msg.playerBestiaId)
    val standsAt = bestia?.let { world.read { get(it.entityId, Position::class)?.toVec3L() } }
      ?: return deny(msg.playerId, OpError.AI_CONFIG_BESTIA_NOT_OWNED)

    val refusal = places.saveArea(masterId, null, msg.name, standsAt, msg.radius).refusal
    if (refusal != null) {
      LOG.debug { "Account ${msg.playerId} could not name work area '${msg.name}': $refusal" }
      return deny(msg.playerId, errorOf(refusal))
    }

    out.sendToPlayer(msg.playerId, book.of(masterId))
    return true
  }

  private fun errorOf(refusal: NamedPlaceService.Refusal) = when (refusal) {
    NamedPlaceService.Refusal.NAME_TAKEN -> OpError.ORDERS_NAME_TAKEN
    NamedPlaceService.Refusal.TOO_MANY -> OpError.ORDERS_TOO_MANY_PLACES
    NamedPlaceService.Refusal.NOT_FOUND, NamedPlaceService.Refusal.INVALID_NAME, NamedPlaceService.Refusal.NOT_A_CHEST ->
      OpError.REQUEST_REFUSED
  }

  private fun deny(accountId: Long, code: OpError): Boolean {
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
    return false
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}

package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.master.bestia.OrderBook
import net.bestia.zone.master.bestia.OrderPresetService
import net.bestia.zone.master.bestia.OrdersWire
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/**
 * Saves a preset from the orders window. Answers the whole order book, as every orders-window write does, so the
 * window never has to merge a change into what it shows.
 */
@Component
class SaveOrderPresetHandler(
  private val sessions: ConnectionInfoService,
  private val presets: OrderPresetService,
  private val book: OrderBook,
  private val out: OutMessageProcessor,
) : IoMessageHandler<SaveOrderPresetCMSG> {

  override val wire = decoder(MessageCase.SAVE_ORDER_PRESET) { accountId, envelope ->
    val msg = envelope.saveOrderPreset
    SaveOrderPresetCMSG(accountId, msg.takeIf { it.hasPresetId() }?.presetId, msg.name, OrdersWire.ordersFrom(msg.orders))
  }

  override fun handle(msg: SaveOrderPresetCMSG): Boolean {
    val masterId = sessions.activeMasterIdOf(msg.playerId) ?: return false
    val orders = msg.orders ?: return deny(msg.playerId, OpError.AI_CONFIG_INVALID_ORDERS)

    val refusal = presets.save(masterId, msg.presetId, msg.name, orders).refusal
    if (refusal != null) {
      LOG.debug { "Account ${msg.playerId} could not save preset '${msg.name}': $refusal" }
      return deny(msg.playerId, errorOf(refusal))
    }

    out.sendToPlayer(msg.playerId, book.of(masterId))
    return true
  }

  /** The window checks the name and only offers presets it was sent, so the rest is an outdated or dishonest client. */
  private fun errorOf(refusal: OrderPresetService.Refusal) = when (refusal) {
    OrderPresetService.Refusal.NAME_TAKEN -> OpError.ORDERS_NAME_TAKEN
    OrderPresetService.Refusal.TOO_MANY -> OpError.ORDERS_TOO_MANY_PRESETS
    OrderPresetService.Refusal.INVALID_ORDERS -> OpError.AI_CONFIG_INVALID_ORDERS
    OrderPresetService.Refusal.NOT_FOUND, OrderPresetService.Refusal.INVALID_NAME -> OpError.REQUEST_REFUSED
  }

  private fun deny(accountId: Long, code: OpError): Boolean {
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
    return false
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}

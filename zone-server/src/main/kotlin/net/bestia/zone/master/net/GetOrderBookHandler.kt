package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.master.bestia.OrderBook
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Sends the presets and places of the master a player is playing, which the orders window picks from. */
@Component
class GetOrderBookHandler(
  private val sessions: ConnectionInfoService,
  private val book: OrderBook,
  private val out: OutMessageProcessor,
) : IoMessageHandler<GetOrderBookCMSG> {

  override val wire = decoder(MessageCase.GET_ORDER_BOOK) { accountId, _ -> GetOrderBookCMSG(accountId) }

  override fun handle(msg: GetOrderBookCMSG): Boolean {
    val masterId = sessions.activeMasterIdOf(msg.playerId) ?: return false

    out.sendToPlayer(msg.playerId, book.of(masterId))
    return true
  }
}

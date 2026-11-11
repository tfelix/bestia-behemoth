package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.master.bestia.OrderBook
import net.bestia.zone.master.bestia.OrderPresetService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class DeleteOrderPresetHandler(
  private val sessions: ConnectionInfoService,
  private val presets: OrderPresetService,
  private val book: OrderBook,
  private val out: OutMessageProcessor,
) : IoMessageHandler<DeleteOrderPresetCMSG> {

  override val wire = decoder(MessageCase.DELETE_ORDER_PRESET) { accountId, envelope ->
    DeleteOrderPresetCMSG(accountId, envelope.deleteOrderPreset.presetId)
  }

  override fun handle(msg: DeleteOrderPresetCMSG): Boolean {
    val masterId = sessions.activeMasterIdOf(msg.playerId) ?: return false

    // Gone already, say deleted in the companion app: the fresh book shows that.
    presets.delete(masterId, msg.presetId)
    out.sendToPlayer(msg.playerId, book.of(masterId))
    return true
  }
}

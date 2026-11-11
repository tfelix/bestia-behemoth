package net.bestia.zone.master.bestia

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Sends new orders to the game client of a master who is playing, wherever the change came from: an open orders
 * window must not show what the companion app or a preset edit has just replaced.
 */
@Component
class OrdersToGame(
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onOrdersChanged(event: BestiaAiConfigChangedEvent) {
    if (!connectionInfoService.isPlaying(event.accountId, event.masterId)) return

    outMessageProcessor.sendToPlayer(event.accountId, BestiaAiConfigSMSG(event.playerBestiaId, event.stored))
  }
}
